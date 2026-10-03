// SPDX-License-Identifier: Apache-2.0
package com.openinglab.service

import com.openinglab.shared.analysis.*
import com.openinglab.shared.storage.contentSha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlin.concurrent.thread

/** Explicit operator-selected, checksummed separate process. Missing config does not become fake analysis. */
class HostEngine private constructor(val identity: EngineIdentity, val engine: ChessAnalysisEngine) {
    companion object {
        fun load(directory: Path): HostEngine {
            val identityPath = directory.resolve("identity.json")
            require(Files.isRegularFile(identityPath, NOFOLLOW_LINKS) && Files.size(identityPath) <= 65_536)
            val json = codec.parseToJsonElement(Files.readString(identityPath)).jsonObject
            val networks = json.getValue("networks").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val identity = EngineIdentity(json.getValue("name").jsonPrimitive.content, json.getValue("version").jsonPrimitive.content,
                json.getValue("binarySha256").jsonPrimitive.content, networks)
            require(identity.name == "Stockfish" && identity.version == "19" && json.getValue("license").jsonPrimitive.content == "GPL-3.0-or-later")
            val binary = directory.resolve("stockfish")
            fun check(path: Path, checksum: String, limit: Long) {
                require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) in 1..limit && contentSha256(Files.readAllBytes(path)) == checksum)
            }
            check(binary, identity.binarySha256, 32L * 1024 * 1024)
            networks.forEach { (name, checksum) -> check(directory.resolve(name), checksum, 256L * 1024 * 1024) }
            check(directory.resolve("source.tar"), json.getValue("sourceSha256").jsonPrimitive.content, 16L * 1024 * 1024)
            check(directory.resolve("prepare-service-engine.mjs"), json.getValue("recipeSha256").jsonPrimitive.content, 65_536)
            require(Files.isExecutable(binary) && Files.isRegularFile(directory.resolve("COPYING.txt"), NOFOLLOW_LINKS))
            val factory = UciTransportFactory { withContext(Dispatchers.IO) {
                check(binary, identity.binarySha256, 32L * 1024 * 1024)
                networks.forEach { (name, checksum) -> check(directory.resolve(name), checksum, 256L * 1024 * 1024) }
                ProcessTransport(ProcessBuilder(binary.toAbsolutePath().toString()).directory(directory.toFile()).redirectErrorStream(true).start())
            } }
            return HostEngine(identity, UciAnalysisEngine(identity, factory))
        }
    }
}
private class ProcessTransport(private val process: Process) : UciTransport {
    private val lines = Channel<String>(32)
    private val input = process.outputStream.bufferedWriter()
    private val reader = thread(isDaemon = true, name = "ashva-service-uci") {
        try {
            process.inputStream.reader(Charsets.UTF_8).use { reader ->
                val buffer = StringBuilder()
                while (true) {
                    val char = reader.read()
                    if (char < 0) break
                    if (char == 10) {
                        val line = buffer.toString(); buffer.setLength(0)
                        // Blocking producer into bounded channel; cancellation closes streams/process.
                        kotlinx.coroutines.runBlocking { lines.send(line) }
                    } else {
                        require(buffer.length < 8192); buffer.append(char.toChar())
                    }
                }
                require(buffer.isEmpty())
            }
            lines.close()
        } catch (e: Exception) { lines.close(EngineProtocolException("Host engine stream unavailable")) }
    }
    override suspend fun send(command: String) = withContext(Dispatchers.IO) {
        require(command.length <= 8192 && '\n' !in command && '\r' !in command)
        input.write(command); input.newLine(); input.flush()
    }
    override suspend fun readLine(): String? = lines.receiveCatching().getOrThrow()
    override fun close() {
        lines.cancel(); process.destroyForcibly()
        runCatching { input.close() }; runCatching { process.inputStream.close() }
        reader.interrupt()
        runCatching { process.waitFor(250, java.util.concurrent.TimeUnit.MILLISECONDS) }
        runCatching { reader.join(100) }
    }
}
