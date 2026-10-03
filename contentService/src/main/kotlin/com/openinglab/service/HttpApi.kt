// SPDX-License-Identifier: Apache-2.0
package com.openinglab.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import java.security.MessageDigest
import java.sql.SQLException

class RequestGate(private val now: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex(); private var tokens = 5.0; private var at = now()
    suspend fun admit() = mutex.withLock {
        val current = now(); tokens = minOf(5.0, tokens + (current - at).coerceAtLeast(0) / 1000.0); at = current
        if (tokens < 1) throw ApiFailure("REQUEST_RATE_LIMIT", 429)
        tokens -= 1
    }
}

fun Application.contentApi(repository: PgRepository, objects: PackObjects, queue: WorkQueue,
    token: String, gate: RequestGate = RequestGate()) {
    require(token.length in 32..128)
    install(ContentNegotiation) { json(codec) }
    install(StatusPages) {
        exception<ApiFailure> { call, error -> call.respond(HttpStatusCode.fromValue(error.httpStatus), ApiError(error.code)) }
        exception<SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_JSON")) }
        exception<IllegalArgumentException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_REQUEST")) }
        exception<SQLException> { call, _ -> call.respond(HttpStatusCode.ServiceUnavailable, ApiError("DATABASE_UNAVAILABLE")) }
        exception<IllegalStateException> { call, _ -> call.respond(HttpStatusCode.ServiceUnavailable, ApiError("SERVICE_UNAVAILABLE")) }
    }
    fun ApplicationCall.authorize() {
        response.header(HttpHeaders.CacheControl, "no-store")
        val supplied = request.headers[HttpHeaders.Authorization].orEmpty()
        if (supplied.length > 256 || !MessageDigest.isEqual(supplied.encodeToByteArray(), "Bearer $token".encodeToByteArray())) throw ApiFailure("UNAUTHORIZED", 401)
        if (request.headers[HttpHeaders.Origin] != null) throw ApiFailure("BROWSER_ORIGIN_DENIED", 403)
    }
    suspend fun ApplicationCall.jsonBody(): String {
        if (!request.contentType().match(ContentType.Application.Json)) throw ApiFailure("JSON_REQUIRED", 415)
        val bytes = withTimeoutOrNull(5000) { receiveChannel().readBuffer(65_537L).readByteArray() }
            ?: throw ApiFailure("BODY_READ_TIMEOUT", 408)
        if (bytes.size > 65_536) throw ApiFailure("BODY_TOO_LARGE", 413)
        return bytes.decodeToString(throwOnInvalidSequence = true)
    }
    suspend fun ApplicationCall.jobReply(job: JobView) {
        if (job.status in listOf("PENDING", "RUNNING")) {
            response.header(HttpHeaders.Location, "/v1/jobs/${job.id}"); response.header(HttpHeaders.RetryAfter, "1")
            respond(HttpStatusCode.Accepted, job)
        } else respond(job)
    }
    routing {
        get("/health") { call.respond(ApiError("OK")) }
        get("/v1/catalog") {
            val catalog = repository.catalog(); val etag = "\"${catalog.version}\""
            call.response.header(HttpHeaders.ETag, etag); call.response.header(HttpHeaders.CacheControl, "public, max-age=0, must-revalidate")
            if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) call.respond(HttpStatusCode.NotModified) else call.respond(catalog)
        }
        get("/v1/catalog/delta") { call.respond(repository.delta(call.request.queryParameters["from"] ?: throw ApiFailure("FROM_REQUIRED"))) }
        listOf("openings" to "OPENING", "games" to "GAME").forEach { (route, kind) ->
            get("/v1/$route") {
                val limit = call.request.queryParameters["limit"]?.let { it.toIntOrNull() ?: throw ApiFailure("INVALID_LIMIT") } ?: 50
                call.respond(repository.page(kind, call.request.queryParameters["q"].orEmpty(), limit, call.request.queryParameters["cursor"]))
            }
        }
        get("/v1/packs/{hash}/{file}") {
            val descriptor = repository.pack(call.parameters["hash"].orEmpty())
            val name = call.parameters["file"].orEmpty()
            val (bytes, checksum) = withContext(Dispatchers.IO) { objects.read(descriptor, name) }
            val etag = "\"$checksum\""
            call.response.header(HttpHeaders.ETag, etag); call.response.header("X-Content-SHA256", checksum)
            call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
            if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) call.respond(HttpStatusCode.NotModified)
            else call.respondBytes(bytes, if (name == "manifest.json") ContentType.Application.Json else ContentType.Application.OctetStream)
        }
        post("/v1/jobs/import") { call.authorize(); gate.admit(); call.jobReply(queue.import(codec.decodeFromString<ImportRequest>(call.jsonBody()))) }
        post("/v1/jobs/analysis") { call.authorize(); gate.admit(); call.jobReply(queue.analyze(codec.decodeFromString<AnalysisRequest>(call.jsonBody()))) }
        get("/v1/jobs/{id}") { call.authorize(); call.respond(repository.job(call.parameters["id"].orEmpty())) }
        post("/v1/jobs/{id}/cancel") { call.authorize(); call.respond(queue.cancel(call.parameters["id"].orEmpty())) }
        get("/v1/metrics") { call.authorize(); call.respond(repository.metrics()) }
    }
}
