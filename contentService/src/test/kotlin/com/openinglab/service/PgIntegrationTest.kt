// SPDX-License-Identifier: Apache-2.0
package com.openinglab.service

import com.openinglab.shared.analysis.*
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.storage.contentSha256
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class PgIntegrationTest {
    private data class Environment(val repository: PgRepository, val objects: PackObjects, val root: Path, val token: String,
        val objectsRoot: Path, val jdbc: String, val schema: String)
    private suspend fun environment(now: () -> Long = System::currentTimeMillis): Environment {
        val url = requireNotNull(System.getenv("ASHVA_SERVICE_TEST_JDBC")) { "Set the explicit disposable PostgreSQL test URL; integration checks never skip silently" }
        require(url.matches(Regex("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/ashva_test"))) { "Integration checks require the test-only database, never an owner database" }
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val repository = PgRepository(url, "postgres", System.getenv("ASHVA_SERVICE_TEST_PASSWORD").orEmpty(), "ashva_test_$suffix", now)
        repository.initialize()
        val root = Path.of("").toAbsolutePath()
        val objectsRoot = root.resolve("contentService/build/test-objects-$suffix")
        val objects = PackObjects(objectsRoot, PackObjects.reviewed(root))
        return Environment(repository, objects, root, UUID.randomUUID().toString(), objectsRoot, url, "ashva_test_$suffix")
    }
    @Test fun realPackImportsHttpSearchRetainedObjectsPaginationDeltaAndAttribution() = testApplication {
        val env = environment(); val repository = env.repository; val queue = WorkQueue(repository, env.objects)
        application { contentApi(repository, env.objects, queue, env.token) }
        val before = codec.decodeFromString<Catalog>(client.get("/v1/catalog").bodyAsText()); assertTrue(before.packs.isEmpty())
        for (pack in PackObjects.reviewed(env.root)) {
            val response = client.post("/v1/jobs/import") { bearerAuth(env.token); contentType(ContentType.Application.Json); setBody(codec.encodeToString(ImportRequest(pack.id))) }
            assertEquals(HttpStatusCode.Accepted, response.status)
            val job = codec.decodeFromString<JobView>(response.bodyAsText()); assertTrue(queue.drainOnce())
            assertEquals("SUCCEEDED", repository.job(job.id).status)
        }
        val response = client.get("/v1/catalog"); val current = codec.decodeFromString<Catalog>(response.bodyAsText())
        assertEquals(3, current.packs.size); assertNotEquals(before.version, current.version)
        assertEquals(HttpStatusCode.NotModified, client.get("/v1/catalog") { header(HttpHeaders.IfNoneMatch, response.headers[HttpHeaders.ETag]!!) }.status)
        assertEquals(3, repository.delta(before.version).changed.size)
        assertTrue(repository.delta(current.version).changed.isEmpty())
        val openingPage = codec.decodeFromString<ContentPage>(client.get("/v1/openings?q=ruy%20lopez&limit=7").bodyAsText())
        assertEquals(7, openingPage.items.size); assertNotNull(openingPage.nextCursor)
        assertEquals("CC0-1.0", openingPage.sources.single().manifest.source.license)
        var cursor: String? = null; val ids = mutableSetOf<String>()
        do {
            val page = repository.page("OPENING", "Ruy Lopez", 100, cursor)
            page.items.forEach { assertTrue(ids.add(it.recordId)) }; cursor = page.nextCursor
        } while (cursor != null)
        assertEquals(235, ids.size)
        assertEquals(3815, repository.page("OPENING", "", 100, null).sources.single().manifest.coverage.acceptedRecords)
        val gamePage = repository.page("GAME", "", 100, null)
        assertEquals(100, gamePage.items.size); assertTrue(gamePage.sources.all { it.manifest.source.license == "CC-BY-SA-4.0" })
        var gameCursor: String? = null; var games = 0
        do { val page = repository.page("GAME", "", 100, gameCursor); games += page.items.size; gameCursor = page.nextCursor } while (gameCursor != null)
        assertEquals(936, games)
        assertEquals(0, repository.page("OPENING", "%", 20, null).items.size) // literal, not wildcard/injection
        assertFailsWith<IllegalArgumentException> { repository.page("GAME", "Ruy Lopez", 100, openingPage.nextCursor) }
        assertFailsWith<ApiFailure> { repository.delta("f".repeat(64)) }
        val pack = current.packs.first()
        val download = client.get(pack.manifestUrl)
        assertEquals(pack.manifestSha256, contentSha256(download.bodyAsText().encodeToByteArray()))
        assertEquals(HttpStatusCode.NotModified, client.get(pack.manifestUrl) { header(HttpHeaders.IfNoneMatch, download.headers[HttpHeaders.ETag]!!) }.status)
        val file = pack.manifest.files.first { it.name == "ATTRIBUTION.txt" }
        assertEquals(file.sha256, contentSha256(client.get("/v1/packs/${pack.manifestSha256}/${file.name}").bodyAsText().encodeToByteArray()))
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/packs/${pack.manifestSha256}/private.db").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/metrics").status)
        val metrics = client.get("/v1/metrics") { bearerAuth(env.token) }.bodyAsText()
        assertFalse("request" in metrics); assertFalse("initialFen" in metrics); assertFalse(env.token in metrics)
        queue.close()
    }
    @Test fun stalePaginationRetainsOldDownloadsAndRequiresExplicitRestart() = runBlocking {
        val env = environment(); val repository = env.repository
        repository.publish(env.objects.validateAndStore(PackObjects.reviewed(env.root)[0].id))
        val old = repository.catalog(); val page = repository.page("OPENING", "", 1, null)
        repository.publish(env.objects.validateAndStore(PackObjects.reviewed(env.root)[1].id))
        val error = assertFailsWith<ApiFailure> { repository.page("OPENING", "", 1, page.nextCursor) }
        assertEquals("STALE_CURSOR_RESTART", error.code)
        assertEquals(old.packs.single(), repository.pack(old.packs.single().manifestSha256))
        assertEquals(1, repository.delta(old.version).changed.size)
        val original = env.objects.read(old.packs.single(), "games.jsonl")
        assertEquals(0, original.first.size)
    }
    @Test fun concurrentRequestsDeduplicateDurablyAndCacheExpiryIsExplicit() = runBlocking {
        var time = System.currentTimeMillis(); val env = environment { time }; val calls = AtomicInteger()
        val engine = object : ChessAnalysisEngine { override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget): EngineAnalysis {
            calls.incrementAndGet(); return result(AnalysisRequest(position.initialFen, position.moves, originalMove, BudgetRequest(budget.depth, budget.nodes, budget.moveTimeMillis, budget.multiPv, budget.hashMiB)))
        } }
        val request = AnalysisRequest(BoardPosition.START_FEN, originalMove = "e2e4", budget = BudgetRequest(multiPv = 1))
        val queue = WorkQueue(env.repository, env.objects, engine, testIdentity)
        val ids = coroutineScope { (1..12).map { async { queue.analyze(request).id } }.awaitAll() }
        assertEquals(1, ids.distinct().size); assertTrue(queue.drainOnce()); assertFalse(queue.drainOnce()); assertEquals(1, calls.get())
        val cached = queue.analyze(request); assertTrue(cached.cached); assertEquals("SUCCEEDED", cached.status); assertNotNull(cached.result)
        val restarted = WorkQueue(env.repository, env.objects, engine, testIdentity)
        assertTrue(restarted.analyze(request).cached)
        time += 7L * 24 * 3600 * 1000 + 1
        val expired = env.repository.job(cached.id); assertEquals("EXPIRED", expired.status); assertNull(expired.result)
        assertEquals("PENDING", restarted.analyze(request).status); assertTrue(restarted.drainOnce()); assertEquals(2, calls.get())
        assertNotEquals(cached.id, queue.analyze(request.copy(moves = listOf("g1f3", "g8f6", "f3g1", "f6g8"))).id)
        queue.close(); restarted.close()
    }
    @Test fun leaseRecoveryRejectsStaleCompletionAndCancellationRejectsPublication() = runBlocking {
        var time = 1000L; val env = environment { time }; val repository = env.repository
        val request = AnalysisRequest(BoardPosition.START_FEN, budget = BudgetRequest(multiPv = 1))
        val queue = WorkQueue(repository, env.objects, object : ChessAnalysisEngine {
            override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget) = result(request)
        }, testIdentity)
        val id = queue.analyze(request).id; val first = repository.claim()!!
        time += 240_001; val second = repository.claim()!!; assertEquals(2, second.attempts)
        assertFalse(repository.complete(first, JsonPrimitive("stale")))
        repository.cancel(id); assertFalse(repository.complete(second, JsonPrimitive("cancelled"))); assertEquals("CANCELLED", repository.job(id).status)
        val spec = PackObjects.reviewed(env.root).first(); val importId = queue.import(ImportRequest(spec.id)).id
        val importClaim = repository.claim()!!; val validated = env.objects.validateAndStore(spec.id)
        repository.cancel(importId)
        assertFailsWith<ApiFailure> { repository.publish(validated, importClaim) }
        assertTrue(repository.catalog().packs.isEmpty()); assertEquals("CANCELLED", repository.job(importId).status)
        queue.close()
    }
    @Test fun retryBudgetAndMalformedOutputNeverBecomeCachedSuccess() = runBlocking {
        var time = 1000L; val env = environment { time }; val calls = AtomicInteger()
        val request = AnalysisRequest(BoardPosition.START_FEN, budget = BudgetRequest(multiPv = 1))
        val queue = WorkQueue(env.repository, env.objects, object : ChessAnalysisEngine {
            override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget): EngineAnalysis { calls.incrementAndGet(); throw java.io.IOException("synthetic private diagnostics must not persist") }
        }, testIdentity)
        val id = queue.analyze(request).id
        repeat(3) { assertTrue(queue.drainOnce()); assertNull(env.repository.job(id).result); time += 60_000 }
        assertEquals("FAILED", env.repository.job(id).status); assertEquals("IO_UNAVAILABLE", env.repository.job(id).errorCode); assertFalse(queue.drainOnce()); assertEquals(3, calls.get())
        val bad = WorkQueue(env.repository, env.objects, object : ChessAnalysisEngine {
            override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget) = result(request).copy(original = null)
        }, testIdentity)
        val invalidId = bad.analyze(request.copy(originalMove = "e2e4")).id; assertTrue(bad.drainOnce())
        assertEquals("FAILED", env.repository.job(invalidId).status); assertNull(env.repository.job(invalidId).result)
        queue.close(); bad.close()
    }
    @Test fun httpAuthorizationBodyValidationNoEngineAndAdmissionFailClosed() = testApplication {
        val env = environment(); val queue = WorkQueue(env.repository, env.objects)
        application { contentApi(env.repository, env.objects, queue, env.token) }
        suspend fun send(body: String, token: Boolean = true, type: ContentType = ContentType.Application.Json) = client.post("/v1/jobs/analysis") {
            if (token) bearerAuth(env.token); contentType(type); setBody(body)
        }
        val request = codec.encodeToString(AnalysisRequest(BoardPosition.START_FEN))
        assertEquals(HttpStatusCode.Unauthorized, send(request, token = false).status)
        assertEquals(HttpStatusCode.UnsupportedMediaType, send(request, type = ContentType.Text.Plain).status)
        assertEquals(HttpStatusCode.BadRequest, send("{\"unexpected\":true}").status)
        assertEquals(HttpStatusCode.PayloadTooLarge, send(" ".repeat(65_537)).status)
        val unavailable = send(request); assertEquals(HttpStatusCode.ServiceUnavailable, unavailable.status); assertEquals("ENGINE_NOT_CONFIGURED", codec.decodeFromString<ApiError>(unavailable.bodyAsText()).code)
        assertEquals(HttpStatusCode.Forbidden, client.post("/v1/jobs/import") { bearerAuth(env.token); header(HttpHeaders.Origin, "https://example.test"); contentType(ContentType.Application.Json); setBody("{}") }.status)
        assertEquals(HttpStatusCode.ServiceUnavailable, send(request).status)
        assertEquals(HttpStatusCode.TooManyRequests, send(request).status)
        assertEquals(0, env.repository.metrics().jobs.values.sum())
        queue.close()
    }
    @Test fun actualHostStockfishRunsThroughDurableQueueAndCheckedResult() = runBlocking {
        val env = environment(); val directory = requireNotNull(System.getenv("ASHVA_SERVICE_TEST_ENGINE_DIR")) { "Prepare the reviewed separate host engine; actual engine integration never skips" }
        val host = HostEngine.load(Path.of(directory)); val queue = WorkQueue(env.repository, env.objects, host.engine, host.identity)
        val request = AnalysisRequest(BoardPosition.START_FEN, listOf("e2e4", "e7e5", "g1f3", "b8c6"), "f1b5", BudgetRequest(depth = 8, nodes = 10_000, moveTimeMillis = 1000, multiPv = 2))
        val id = queue.analyze(request).id; assertTrue(queue.drainOnce())
        val job = env.repository.job(id); assertEquals("SUCCEEDED", job.status, job.errorCode)
        val result = codec.decodeFromJsonElement<AnalysisResult>(job.result!!)
        assertEquals(request, result.request); assertEquals(host.identity.binarySha256, result.engine.binarySha256)
        assertEquals("f1b5", result.original!!.lines.single().uci.first()); assertEquals("Bb5", result.original.lines.single().san.first())
        assertEquals("WHITE", result.rootSide); assertTrue(queue.analyze(request).cached); assertFalse(queue.drainOnce())
        val black = request.copy(moves = listOf("e2e4"), originalMove = "c7c5")
        val blackId = queue.analyze(black).id; assertTrue(queue.drainOnce())
        val blackResult = codec.decodeFromJsonElement<AnalysisResult>(env.repository.job(blackId).result!!)
        assertEquals("BLACK", blackResult.rootSide); assertEquals("c5", blackResult.original!!.lines.single().san.first())
        val repetition = request.copy(moves = List(4) { listOf("g1f3", "g8f6", "f3g1", "f6g8") }.flatten(), originalMove = null)
        val terminalId = queue.analyze(repetition).id; assertTrue(queue.drainOnce())
        val terminal = codec.decodeFromJsonElement<AnalysisResult>(env.repository.job(terminalId).result!!)
        assertNotNull(terminal.terminal); assertNull(terminal.alternatives); assertNull(terminal.original)
        queue.close()
    }
    @Test fun failedDependencyAndImmutableConflictRollBackWhileNewVersionRetainsOldMetadata() = runBlocking {
        val env = environment(); val specs = PackObjects.reviewed(env.root)
        val game = env.objects.validateAndStore(specs[1].id)
        assertFailsWith<IllegalArgumentException> { env.repository.publish(game) }
        assertTrue(env.repository.catalog().packs.isEmpty())
        val original = env.objects.validateAndStore(specs[0].id); env.repository.publish(original)
        val old = env.repository.catalog()
        val manifest = original.descriptor.manifest.copy(packId = "ashva-synthetic-test-version", source = original.descriptor.manifest.source.copy(revision = "synthetic-test-only"))
        val newer = original.copy(descriptor = PackDescriptor(hash(codec.encodeToString(manifest)), manifest))
        env.repository.publish(newer)
        assertEquals(newer.descriptor, env.repository.catalog().packs.single())
        assertEquals(original.descriptor, env.repository.pack(original.descriptor.manifestSha256))
        assertEquals(newer.descriptor, env.repository.delta(old.version).changed.single())
        assertFailsWith<IllegalArgumentException> { env.repository.publish(newer.copy(descriptor = newer.descriptor.copy(manifestSha256 = "f".repeat(64)))) }
        assertEquals(newer.descriptor, env.repository.catalog().packs.single())
    }
    @Test fun corruptedObjectAndUntrustedManifestNeverProduceDownloadableSuccess() = runBlocking {
        val env = environment(); val spec = PackObjects.reviewed(env.root).first()
        val bad = PackObjects(env.objectsRoot.resolve("bad-trust"), listOf(spec.copy(manifestSha256 = "f".repeat(64))))
        assertFailsWith<IllegalArgumentException> { bad.bundle(spec.id) }
        val validated = env.objects.validateAndStore(spec.id); env.repository.publish(validated)
        Files.write(env.objectsRoot.resolve(spec.manifestSha256).resolve("ATTRIBUTION.txt"), "Synthetic corrupted test object".encodeToByteArray())
        val error = assertFailsWith<ApiFailure> { env.objects.read(validated.descriptor, "ATTRIBUTION.txt") }
        assertEquals(503, error.httpStatus); assertEquals("OBJECT_INTEGRITY_FAILURE", error.code)
        assertEquals(validated.descriptor, env.repository.pack(spec.manifestSha256))
    }
    @Test fun singleWorkerSerializesEngineWorkAndActiveCancellationStopsIt() = runBlocking {
        val env = environment(); val active = AtomicInteger(); val maximum = AtomicInteger()
        val engine = object : ChessAnalysisEngine {
            override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget): EngineAnalysis {
                val current = active.incrementAndGet(); maximum.updateAndGet { maxOf(it, current) }
                try { delay(100); return result(AnalysisRequest(position.initialFen, position.moves, originalMove,
                    BudgetRequest(budget.depth, budget.nodes, budget.moveTimeMillis, budget.multiPv, budget.hashMiB))) }
                finally { active.decrementAndGet() }
            }
        }
        val request = AnalysisRequest(BoardPosition.START_FEN, budget = BudgetRequest(multiPv = 1))
        val queue = WorkQueue(env.repository, env.objects, engine, testIdentity)
        queue.analyze(request); queue.analyze(request.copy(budget = request.budget.copy(nodes = 999)))
        coroutineScope { listOf(async { queue.drainOnce() }, async { queue.drainOnce() }).awaitAll() }
        assertEquals(1, maximum.get()); assertEquals(0, active.get()); queue.close()
        val started = CompletableDeferred<Unit>(); val stopped = CompletableDeferred<Unit>()
        val cancelling = WorkQueue(env.repository, env.objects, object : ChessAnalysisEngine {
            override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget): EngineAnalysis {
                started.complete(Unit); try { awaitCancellation() } finally { stopped.complete(Unit) }
            }
        }, testIdentity)
        val id = cancelling.analyze(request.copy(budget = request.budget.copy(nodes = 998))).id
        cancelling.start(); withTimeout(5000) { started.await() }; cancelling.cancel(id); withTimeout(5000) { stopped.await() }
        cancelling.close(); assertEquals("CANCELLED", env.repository.job(id).status); assertNull(env.repository.job(id).result)
    }
    @Test fun capacityRejectsDistinctWorkWithoutPruningRetainedRows() = runBlocking {
        val env = environment()
        java.sql.DriverManager.getConnection(env.jdbc, "postgres", System.getenv("ASHVA_SERVICE_TEST_PASSWORD").orEmpty()).use { connection ->
            connection.createStatement().use { it.execute("SET search_path TO ${env.schema}") }
            connection.prepareStatement("INSERT INTO jobs(id,kind,request,status,available_at) VALUES (?,'ANALYSIS','{}'::jsonb,'CANCELLED',0)").use { statement ->
                repeat(1000) { statement.setString(1, hash("synthetic-capacity-$it")); statement.addBatch() }; statement.executeBatch()
            }
        }
        val queue = WorkQueue(env.repository, env.objects, object : ChessAnalysisEngine {
            override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget) = error("Capacity must reject before any engine work")
        }, testIdentity)
        assertEquals("JOB_CAPACITY_REACHED", assertFailsWith<ApiFailure> { queue.analyze(AnalysisRequest(BoardPosition.START_FEN)) }.code)
        assertEquals(1000, env.repository.metrics().jobs.values.sum()); assertEquals("CANCELLED", env.repository.job(hash("synthetic-capacity-0")).status)
        queue.close()
    }
    @Test fun restartWithChangedEngineCannotCompleteAnOldIdentityJob() = runBlocking {
        val env = environment(); val request = AnalysisRequest(BoardPosition.START_FEN, budget = BudgetRequest(multiPv = 1))
        val engine = object : ChessAnalysisEngine {
            override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget) = error("Wrong-engine job must fail before process execution")
        }
        val first = WorkQueue(env.repository, env.objects, engine, testIdentity)
        val oldId = first.analyze(request).id; first.close()
        val changed = testIdentity.copy(binarySha256 = "c".repeat(64))
        val restarted = WorkQueue(env.repository, env.objects, engine, changed)
        assertTrue(restarted.drainOnce())
        assertEquals("FAILED", env.repository.job(oldId).status); assertEquals("ENGINE_IDENTITY_CHANGED", env.repository.job(oldId).errorCode)
        assertNull(env.repository.job(oldId).result)
        assertNotEquals(oldId, restarted.analyze(request).id)
        assertFailsWith<IllegalArgumentException> { env.repository.enqueue(oldId, "ANALYSIS", "{}") }
        restarted.close()
    }
}
