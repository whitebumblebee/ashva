package com.openinglab.shared.storage

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.openinglab.shared.content.*
import com.openinglab.shared.lesson.*
import com.openinglab.shared.data.OpeningCatalog
import com.openinglab.shared.model.PieceColor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class RoomLearningStoreTest {
    private val json = Json { encodeDefaults = true }
    private val taxonomyId = "lichess-openings-c67912be581f-import-v1"
    private val broadcastId = "lichess-broadcast-2020-04-2020-04-snap-import-v1"
    private fun bundle(id: String): PackBundle {
        val path = Path.of("content/packs/$id")
        val manifest = Files.readAllBytes(path.resolve("manifest.json"))
        val m = json.decodeFromString<ContentManifest>(manifest.decodeToString())
        return PackBundle(manifest, m.files.associate { it.name to Files.readAllBytes(path.resolve(it.name)) }, contentSha256(manifest))
    }
    private fun revised(base: PackBundle, id: String, edit: (ContentManifest) -> ContentManifest = { it }): PackBundle {
        val manifest = edit(json.decodeFromString<ContentManifest>(base.manifest.decodeToString()).copy(packId = id))
        val bytes = json.encodeToString(manifest).encodeToByteArray()
        return base.copy(manifest = bytes, expectedManifestSha256 = contentSha256(bytes))
    }
    private fun small(): PackBundle {
        val base = bundle(taxonomyId)
        val row = base.files.getValue("openings.jsonl").decodeToString().lineSequence().first().plus("\n").encodeToByteArray()
        val record = json.decodeFromString<OpeningRecord>(row.decodeToString().trim())
        val files = base.files + ("openings.jsonl" to row)
        return revised(base.copy(files = files), "fixture-v1") { it.copy(
            files = it.files.map { file -> file.copy(bytes = files.getValue(file.name).size.toLong(), sha256 = contentSha256(files.getValue(file.name))) },
            coverage = CoverageCounts(1, 1, 0, 0, families = 1, distinctNames = 1, distinctPositions = 1,
                minPlies = record.uci.size, maxPlies = record.uci.size)) }
    }
    private fun bookmark(): LessonBookmark {
        val graph = LessonGraph.fromOpening(OpeningCatalog.openings.first())
        return LessonBookmark(graph.id, "a".repeat(64), graph.start(PieceColor.BLACK).jump(3).snapshot(), "STUDY", studied = true)
    }
    private fun database(test: suspend (Path, LearningDatabase, RoomLearningStore) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("opening-lab-store-test-")
        val path = dir.resolve("learning.db")
        val db = createJvmLearningDatabase(path.toString())
        try { test(path, db, RoomLearningStore(db)) }
        finally { db.close(); Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }

    @Test fun emptyStoreAndLearnerStateSurviveColdReopen() = database { path, db, store ->
        assertTrue(store.activePacks().isEmpty())
        assertTrue(store.availability.first().isEmpty()) // catalog overlays MISSING, not a fabricated downloaded row
        val bookmark = bookmark()
        val attempt = LearningAttempt("attempt-1", bookmark.lessonId, bookmark.replay.rootPathId, 3, "BLACK", "a7a6", "EXPECTED", true, 123)
        val selection = RepertoireSelection(bookmark.lessonId, "BLACK", bookmark.replay.rootPathId)
        store.saveBookmark(bookmark); store.recordAttempt(attempt); store.selectRepertoire(selection)
        db.close()
        val reopened = createJvmLearningDatabase(path.toString())
        try {
            val next = RoomLearningStore(reopened)
            assertEquals(bookmark, next.latestBookmark())
            assertEquals(listOf(attempt), next.attempts(bookmark.lessonId))
            assertEquals(listOf(selection), next.repertoires())
        } finally { reopened.close() }
    }

    @Test fun installUpdateRollbackRetainsProgressAndOldContent() = database { _, _, store ->
        val base = small()
        store.install(base); store.saveBookmark(bookmark())
        val original = store.openings().single()
        store.install(base) // idempotent
        store.install(revised(base, "fixture-v2"))
        assertEquals("fixture-v2", store.activePacks().single().manifest.packId)
        assertEquals(original, store.openings("fixture-v1").single())
        store.activatePreviousVersion("lichess-openings", "fixture-v1")
        assertEquals("fixture-v1", store.activePacks().single().manifest.packId)
        assertEquals(bookmark(), store.latestBookmark())
        assertTrue(store.activePacks().single().notices.contains("Lichess"))
        assertEquals(listOf(original.id), store.openingIdsAt(original.positionKey))
        assertFailsWith<IllegalArgumentException> { store.activatePreviousVersion("wrong-source", "fixture-v2") }
    }

    @Test fun brokenChecksumOrMissingFileNeverReplacesActiveContent() = database { _, _, store ->
        val base = small(); store.install(base)
        val broken = revised(base, "fixture-v2").let { it.copy(files = it.files + ("openings.jsonl" to "corrupt".encodeToByteArray())) }
        assertFailsWith<IllegalArgumentException> { store.install(broken) }
        assertEquals("ERROR", store.availability.first().single().state)
        assertEquals("fixture-v1", store.activePacks().single().manifest.packId)
        assertFailsWith<IllegalArgumentException> { store.install(base.copy(files = base.files - "games.jsonl")) }
        assertFailsWith<IllegalArgumentException> { store.install(base.copy(expectedManifestSha256 = "f".repeat(64))) }
        assertEquals(1, store.openings().size)
    }

    @Test fun transactionFailureAndCancellationRollBackAllRows() = database { _, db, store ->
        val base = small(); store.install(base)
        for (cancel in listOf(false, true)) {
            val failing = RoomLearningStore(db, installCheckpoint = { stage ->
                if (stage == "before-activation") {
                    if (cancel) throw CancellationException("test interruption") else error("test failure after rows")
                }
            })
            assertFails { failing.install(revised(base, "fixture-v2")) }
            assertNull(db.learningDao().packById("fixture-v2"))
            assertTrue(store.openings("fixture-v2").isEmpty())
            assertEquals("fixture-v1", store.activePacks().single().manifest.packId)
            assertEquals("ERROR", store.availability.first().single().state)
        }
    }

    @Test fun abandonedLoadingIsRecoveredOnRelaunch() = database { _, db, store ->
        store.install(small())
        db.learningDao().job(InstallJobEntity("lichess-openings", "fixture-v2", "LOADING", null))
        store.recoverInterruptedInstalls()
        val state = store.availability.first().single()
        assertEquals("ERROR", state.state); assertEquals("fixture-v1", state.activePackId)
        assertTrue(state.error!!.contains("interrupted"))
    }

    @Test fun unsupportedSchemaAndImmutableIdConflictAreRejected() = database { _, _, store ->
        val base = small(); store.install(base)
        assertFailsWith<IllegalArgumentException> { store.install(revised(base, "fixture-v3") { it.copy(schemaVersion = 99) }) }
        assertFailsWith<IllegalArgumentException> { store.install(revised(base, "fixture-v1") { it.copy(retrievedAt = "changed") }) }
        assertEquals("fixture-v1", store.activePacks().single().manifest.packId)
    }

    @Test fun checksummedButInconsistentLegalDataAndCountsAreRejected() = database { _, _, store ->
        val base = small(); store.install(base)
        val record = json.decodeFromString<OpeningRecord>(base.files.getValue("openings.jsonl").decodeToString().trim())
        val bytes = (json.encodeToString(record.copy(uci = listOf("e2e5") + record.uci.drop(1))) + "\n").encodeToByteArray()
        val changed = revised(base.copy(files = base.files + ("openings.jsonl" to bytes)), "bad-replay-fixture") { m ->
            m.copy(files = m.files.map { if (it.name == "openings.jsonl") it.copy(bytes = bytes.size.toLong(), sha256 = contentSha256(bytes)) else it })
        }
        assertFailsWith<IllegalArgumentException> { store.install(changed) }
        assertFailsWith<IllegalArgumentException> { store.install(revised(base, "bad-count-fixture") { it.copy(coverage = it.coverage.copy(distinctNames = 99)) }) }
        assertEquals("fixture-v1", store.activePacks().single().manifest.packId)
    }

    @Test fun futureDatabaseVersionFailsWithoutDeletingLearnerData() = database { path, db, store ->
        store.saveBookmark(bookmark()); db.close()
        BundledSQLiteDriver().open(path.toString()).use { it.execSQL("PRAGMA user_version = 99") }
        val future = createJvmLearningDatabase(path.toString())
        try { assertFailsWith<IllegalStateException> { RoomLearningStore(future).latestBookmark() } } finally { future.close() }
        BundledSQLiteDriver().open(path.toString()).use { connection ->
            connection.prepare("SELECT payload FROM bookmarks").use { statement ->
                assertTrue(statement.step()); assertEquals(bookmark(), json.decodeFromString<LessonBookmark>(statement.getText(0)))
            }
        }
    }

    @Test fun pinnedRealPacksReopenOfflineWithExactGameDependency() = database { path, db, store ->
        val games = bundle(broadcastId)
        assertFailsWith<IllegalArgumentException> { store.install(games) }
        store.install(bundle(taxonomyId)); store.install(games)
        assertEquals(3815, store.openings().size)
        assertEquals(setOf("CC0-1.0", "CC-BY-SA-4.0"), store.activePacks().map { it.manifest.source.license }.toSet())
        val expected = json.decodeFromString<GameRecord>(games.files.getValue("games.jsonl").decodeToString().lineSequence().first())
        store.saveBookmark(bookmark())
        db.close()
        val reopened = createJvmLearningDatabase(path.toString())
        try {
            val offline = RoomLearningStore(reopened)
            assertEquals(expected, offline.game(expected.id))
            val graph = offline.game(expected.id)!!.toLessonGraph()
            for (side in PieceColor.entries) assertEquals(expected.finalFen, graph.start(side).last().position.toFen())
            assertEquals(bookmark(), offline.latestBookmark())
            // Retained dependency remains readable even when a newer taxonomy is active.
            offline.install(revised(bundle(taxonomyId), "taxonomy-updated-fixture"))
            assertEquals(3815, offline.openings(taxonomyId).size)
            offline.install(games)
        } finally { reopened.close() }
    }

    @Test fun migrationOneToTwoPreservesLearnerRows() = runBlocking {
        val dir = Files.createTempDirectory("opening-lab-migration-test-")
        val path = dir.resolve("learning.db")
        try {
            val schema = json.parseToJsonElement(Files.readString(Path.of("shared/schemas/com.openinglab.shared.storage.LearningDatabase/1.json")))
                .jsonObject.getValue("database").jsonObject
            BundledSQLiteDriver().open(path.toString()).use { connection ->
                for (entity in schema.getValue("entities").jsonArray) {
                    val e = entity.jsonObject; val name = e.getValue("tableName").jsonPrimitive.content
                    connection.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                    e["indices"]?.jsonArray?.forEach { connection.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name)) }
                }
                schema.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
                connection.prepare("INSERT INTO bookmarks VALUES (?, ?, ?)").use { s ->
                    s.bindText(1, bookmark().lessonId); s.bindText(2, json.encodeToString(bookmark())); s.bindLong(3, 123); s.step()
                }
                connection.execSQL("PRAGMA user_version = 1")
            }
            val db = createJvmLearningDatabase(path.toString())
            try { assertEquals(bookmark(), RoomLearningStore(db).latestBookmark()); assertTrue(RoomLearningStore(db).activePacks().isEmpty()) }
            finally { db.close() }
        } finally { Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
