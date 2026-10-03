// SPDX-License-Identifier: Apache-2.0
package com.openinglab.service

import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

fun main(): Unit = runBlocking {
    // Never log configuration values or accept non-loopback binding without a deployment decision.
    fun required(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() } ?: error("Required local service configuration missing: $name")
    val repository = PgRepository(required("ASHVA_SERVICE_JDBC"), required("ASHVA_SERVICE_DB_USER"), System.getenv("ASHVA_SERVICE_DB_PASSWORD").orEmpty())
    val token = required("ASHVA_SERVICE_TOKEN").also { require(it.length in 32..128) }
    val root = Path.of("").toAbsolutePath()
    val objects = PackObjects(root.resolve("contentService/build/local-objects"), PackObjects.reviewed(root))
    val hostEngine = System.getenv("ASHVA_SERVICE_ENGINE_DIR")?.let { HostEngine.load(Path.of(it)) }
    val port = System.getenv("ASHVA_SERVICE_PORT")?.toIntOrNull() ?: 8080
    require(port in 1024..65535)
    repository.initialize()
    val queue = WorkQueue(repository, objects, hostEngine?.engine, hostEngine?.identity)
    embeddedServer(CIO, host = "127.0.0.1", port = port) {
        contentApi(repository, objects, queue, token)
        monitor.subscribe(ApplicationStarted) { queue.start() }
        monitor.subscribe(ApplicationStopped) { runBlocking { queue.close() } }
    }.start(wait = true)
}
