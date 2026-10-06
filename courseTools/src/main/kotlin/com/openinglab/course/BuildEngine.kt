// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.storage.contentSha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Build-time standard-UCI driver for the separately built host Stockfish (docs/CONTENT_SERVICE.md). Unlike the
 * mobile UciAnalysisEngine it keeps one process alive and allows deep searches; it is never shipped in the app.
 * Every PV is replayed with the shared legal rules before use.
 */
class BuildEngine(private val directory: File, threads: Int, hashMiB: Int) : AutoCloseable {
    companion object { const val MAX_MOVE_TIME_MS = 30_000 }
    private val process: Process
    private val input: BufferedWriter
    private val output: BufferedReader

    init {
        val identity = Json.parseToJsonElement(File(directory, "identity.json").readText())
        val expected = (identity as kotlinx.serialization.json.JsonObject)["binarySha256"].toString().trim('"')
        val binary = File(directory, "stockfish")
        require(contentSha256(binary.readBytes()) == expected) { "Host Stockfish binary does not match identity.json" }
        process = ProcessBuilder(binary.absolutePath).directory(directory).redirectErrorStream(true).start()
        input = process.outputStream.bufferedWriter()
        output = process.inputStream.bufferedReader()
        send("uci"); waitFor("uciok")
        send("setoption name Threads value $threads")
        send("setoption name Hash value $hashMiB")
        send("isready"); waitFor("readyok")
    }

    private fun send(command: String) { input.write(command); input.newLine(); input.flush() }
    private fun waitFor(token: String): List<String> {
        val lines = ArrayList<String>()
        while (true) {
            val line = output.readLine() ?: error("Engine exited while waiting for $token")
            lines += line
            if (line == token || line.startsWith("$token ")) return lines
        }
    }

    /** Analyses the position reached by [moves] from [fen]; scores are from the side to move's point of view. */
    fun analyse(fen: String, moves: List<String>, depth: Int, multiPv: Int, searchMoves: List<String> = emptyList()): EngineResult {
        var board = BoardPosition.fromFen(fen)
        for (uci in moves) board = board.apply(ChessMove.fromUci(uci))
        val legal = board.legalMoves().map { it.uci }.toSet()
        require(searchMoves.all { it in legal }) { "Search move not legal" }
        if (legal.isEmpty()) return EngineResult(emptyList(), depth)
        send("setoption name MultiPV value ${multiPv.coerceAtMost(legal.size)}")
        send("position fen $fen" + if (moves.isEmpty()) "" else " moves ${moves.joinToString(" ")}")
        // Depth is the target; the time cap stops rare positions (deep endgames) whose search explodes.
        send("go depth $depth movetime $MAX_MOVE_TIME_MS" + if (searchMoves.isEmpty()) "" else " searchmoves ${searchMoves.joinToString(" ")}")
        val lines = waitFor("bestmove")
        val latest = HashMap<Int, EngineLine>()
        var finalDepth = 0
        for (line in lines) {
            if (!line.startsWith("info ") || " pv " !in line || " bound" in line.substringBefore(" pv ")) continue
            val t = line.split(' ')
            fun value(name: String) = t.indexOf(name).takeIf { it >= 0 }?.let { t.getOrNull(it + 1) }
            val d = value("depth")?.toIntOrNull() ?: continue
            val rank = value("multipv")?.toIntOrNull() ?: 1
            val scoreAt = t.indexOf("score")
            val cp = if (t.getOrNull(scoreAt + 1) == "cp") t[scoreAt + 2].toInt() else null
            val mate = if (t.getOrNull(scoreAt + 1) == "mate") t[scoreAt + 2].toInt() else null
            val pv = t.subList(t.indexOf("pv") + 1, t.size)
            if (d >= (latest[rank]?.depth ?: 0)) latest[rank] = EngineLine(rank, d, cp, mate, checkedPv(board, pv))
            finalDepth = maxOf(finalDepth, d)
        }
        val result = latest.values.sortedBy { it.rank }.filter { it.pv.isNotEmpty() }
        require(result.isNotEmpty()) { "Engine returned no principal variation" }
        return EngineResult(result, finalDepth)
    }

    private fun checkedPv(board: BoardPosition, pv: List<String>): List<String> {
        var current = board
        val kept = ArrayList<String>()
        for (uci in pv) {
            val move = runCatching { ChessMove.fromUci(uci) }.getOrNull() ?: break
            if (!current.isLegal(move)) break
            current = current.sanAndPlay(move).position
            kept += uci
        }
        require(kept.isNotEmpty()) { "Engine PV first move is illegal" }
        return kept
    }

    override fun close() {
        runCatching { send("quit") }
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
    }
}

@Serializable
data class EngineLine(val rank: Int, val depth: Int, val cp: Int? = null, val mate: Int? = null, val pv: List<String>) {
    /** Side-to-move score with mates mapped beyond any centipawn value. */
    val score: Int get() = mate?.let { if (it > 0) 100_000 - it * 100 else -100_000 - it * 100 } ?: requireNotNull(cp)
}

@Serializable
data class EngineResult(val lines: List<EngineLine>, val depth: Int) {
    val best: EngineLine? get() = lines.firstOrNull()
}

fun PieceColor.sign(): Int = if (this == PieceColor.WHITE) 1 else -1
