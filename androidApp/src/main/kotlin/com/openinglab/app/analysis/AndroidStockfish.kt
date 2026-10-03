// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.analysis

import android.content.Context
import android.os.Build
import com.openinglab.shared.analysis.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** A separate, unmodified GPL executable in the APK's read-only native directory; never JNI/dlopen. */
object AndroidStockfish {
    private val networkMutex = Mutex()
    fun engine(context: Context): ChessAnalysisEngine {
        val app = context.applicationContext
        return object : ChessAnalysisEngine {
            override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget): EngineAnalysis =
                withContext(Dispatchers.Default) {
                    val metadata = withContext(Dispatchers.IO) {
                        app.assets.open("engine/identity.json").bufferedReader().use { JSONObject(it.readText()) }
                    }
                    val binaries = metadata.getJSONObject("binaries")
                    val abi = Build.SUPPORTED_ABIS.firstOrNull { binaries.has(it) }
                        ?: error("Offline analysis currently supports ARM64 and x86_64 devices")
                    val networks = metadata.getJSONObject("networks")
                    val identity = EngineIdentity(metadata.getString("name"), metadata.getString("version"),
                        binaries.getString(abi), networks.keys().asSequence().associateWith { networks.getString(it) })
                    val networkDirectory = withTimeout(20_000) { networkMutex.withLock { withContext(Dispatchers.IO) {
                        val directory = File(app.filesDir, "engine-network")
                        check(directory.isDirectory || directory.mkdirs()) { "Engine data directory unavailable" }
                        for ((name, expected) in identity.networks) {
                            val file = File(directory, name)
                            if (file.isFile && digest(file) == expected) continue
                            val temporary = File.createTempFile("network-", ".partial", directory)
                            try {
                                app.assets.open("engine/$name").use { input -> temporary.outputStream().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        output.write(buffer, 0, count)
                                    }
                                } }
                                check(digest(temporary) == expected) { "Engine network checksum mismatch" }
                                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                            } finally { temporary.delete() }
                        }
                        directory
                    } } }
                    val binary = File(app.applicationInfo.nativeLibraryDir, "libstockfish.so")
                    val engine = UciAnalysisEngine(identity, UciTransportFactory {
                        var opened: ProcessUciTransport? = null
                        try { withContext(Dispatchers.IO) {
                            check(binary.isFile && binary.canExecute()) { "Packaged engine unavailable" }
                            check(digest(binary) == identity.binarySha256) { "Packaged engine checksum mismatch" }
                            ProcessUciTransport.start(binary, networkDirectory).also { opened = it }
                        } } catch (error: Throwable) { opened?.close(); throw error }
                    })
                    engine.analyze(position, originalMove, budget)
                }
        }
    }

    private suspend fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Bounded output with cancellation that kills the child and unblocks the reader. */
internal class ProcessUciTransport private constructor(private val process: Process) : UciTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lines = Channel<String>(32)
    private val writer = process.outputStream.bufferedWriter()
    private val reader = process.inputStream.bufferedReader()
    private val closed = AtomicBoolean(false)
    init {
        activeProcesses.incrementAndGet()
        scope.launch {
            try {
                while (true) {
                    val line = StringBuilder()
                    var ended = false
                    while (true) {
                        ensureActive()
                        val char = reader.read()
                        if (char < 0) { ended = true; break }
                        if (char == '\n'.code) break
                        check(line.length < 8_192) { "Engine output exceeds bound" }
                        if (char != '\r'.code) line.append(char.toChar())
                    }
                    if (line.isNotEmpty()) lines.send(line.toString())
                    if (ended) break
                }
                lines.close()
            } catch (error: Exception) { lines.close(error) }
        }
    }
    override suspend fun send(command: String) = runInterruptible(Dispatchers.IO) {
        require('\n' !in command && '\r' !in command)
        writer.write(command); writer.newLine(); writer.flush()
    }
    override suspend fun readLine(): String? = lines.receiveCatching().let { result ->
        result.exceptionOrNull()?.let { throw it }
        result.getOrNull()
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        process.destroy()
        if (process.isAlive) process.destroyForcibly()
        scope.cancel(); lines.cancel()
        runCatching { reader.close() }; runCatching { writer.close() }
        activeProcesses.decrementAndGet()
    }
    companion object {
        internal val activeProcesses = AtomicInteger(0)
        fun start(binary: File, networkDirectory: File): ProcessUciTransport {
            val process = ProcessBuilder(binary.absolutePath).directory(networkDirectory).redirectErrorStream(true).start()
            return try { ProcessUciTransport(process) } catch (error: Throwable) { process.destroyForcibly(); throw error }
        }
    }
}
