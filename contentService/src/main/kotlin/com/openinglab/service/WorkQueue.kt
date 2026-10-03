// SPDX-License-Identifier: Apache-2.0
package com.openinglab.service

import com.openinglab.shared.analysis.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** One bounded worker, durable leases and exact-key deduplication; no automatic private-game uploads. */
class WorkQueue(private val repository: PgRepository, private val objects: PackObjects,
    private val engine: ChessAnalysisEngine? = null, val engineIdentity: EngineIdentity? = null) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val serial = Mutex()
    private val running = ConcurrentHashMap<String, Job>()
    private var loop: Job? = null
    init { require((engine == null) == (engineIdentity == null)) }
    suspend fun import(request: ImportRequest): JobView {
        val spec = objects.spec(request.packId)
        return repository.enqueue(hash("ashva-import/1:${spec.manifestSha256}"), "IMPORT", codec.encodeToString(request))
    }
    suspend fun analyze(request: AnalysisRequest): JobView {
        withContext(Dispatchers.Default) { request.checked() }
        val identity = engineIdentity ?: throw ApiFailure("ENGINE_NOT_CONFIGURED", 503)
        return repository.enqueue(analysisKey(request, identity), "ANALYSIS", codec.encodeToString(AnalysisJobRequest(request, EngineSource.from(identity))))
    }
    suspend fun cancel(id: String): JobView {
        val value = repository.cancel(id); running[id]?.cancel(); return value
    }
    fun start() {
        check(loop == null)
        loop = scope.launch {
            while (isActive) {
                try { if (!drainOnce()) delay(250) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { delay(1000) } // DB outage cannot kill the worker or log secret/raw request diagnostics.
            }
        }
    }
    suspend fun close() { scope.cancel(); scope.coroutineContext[Job]?.join() }
    suspend fun drainOnce(): Boolean = serial.withLock {
        val claim = repository.claim() ?: return@withLock false
        coroutineScope {
            val work = launch(start = CoroutineStart.LAZY) {
                try {
                    if (repository.job(claim.id).status != "RUNNING") return@launch
                    val result = withTimeout(if (claim.kind == "IMPORT") 180_000 else 20_000) {
                        when (claim.kind) {
                            "IMPORT" -> {
                                val request = codec.decodeFromString<ImportRequest>(claim.request)
                                require(claim.id == hash("ashva-import/1:${objects.spec(request.packId).manifestSha256}"))
                                val validated = objects.validateAndStore(request.packId)
                                repository.publish(validated, claim) // Content activation and import completion share one guarded commit.
                                codec.parseToJsonElement(codec.encodeToString(validated.descriptor))
                            }
                            "ANALYSIS" -> {
                                val work = codec.decodeFromString<AnalysisJobRequest>(claim.request)
                                val request = work.request
                                val identity = engineIdentity ?: throw ApiFailure("ENGINE_NOT_CONFIGURED", 503)
                                if (work.protocolVersion != 1 || work.engine.identity() != identity || claim.id != analysisKey(request, identity))
                                    throw ApiFailure("ENGINE_IDENTITY_CHANGED", 409)
                                val value = requireNotNull(engine).analyze(request.checked(), request.originalMove, request.budget.checked())
                                codec.parseToJsonElement(codec.encodeToString(AnalysisResult.checked(request, identity, value)))
                            }
                            else -> throw ApiFailure("UNKNOWN_JOB_KIND")
                        }
                    }
                    if (claim.kind != "IMPORT") repository.complete(claim, result)
                } catch (_: TimeoutCancellationException) {
                    withContext(NonCancellable) { repository.fail(claim, true, "WORK_TIMEOUT") }
                } catch (e: CancellationException) {
                    withContext(NonCancellable) { repository.fail(claim, true, "WORKER_INTERRUPTED") }
                    throw e
                } catch (_: IOException) {
                    repository.fail(claim, true, "IO_UNAVAILABLE")
                } catch (_: java.sql.SQLException) {
                    repository.fail(claim, true, "DATABASE_UNAVAILABLE")
                } catch (_: EngineProtocolException) {
                    repository.fail(claim, false, "ENGINE_PROTOCOL_REJECTED")
                } catch (e: ApiFailure) {
                    repository.fail(claim, false, e.code)
                } catch (_: Exception) {
                    repository.fail(claim, false, "WORK_VALIDATION_REJECTED")
                } finally { running.remove(claim.id) }
            }
            running[claim.id] = work; work.start(); work.join()
        }
        true
    }
}
