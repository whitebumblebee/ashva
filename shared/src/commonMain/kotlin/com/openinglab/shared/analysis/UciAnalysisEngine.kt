// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.analysis

import com.openinglab.shared.chess.PositionStatus
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.model.ChessMove
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Standard text UCI only. No engine source, JNI linkage, network access, or background pondering. */
class UciAnalysisEngine(
    private val identity: EngineIdentity,
    private val factory: UciTransportFactory,
) : ChessAnalysisEngine {
    override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget): EngineAnalysis {
        currentCoroutineContext().ensureActive()
        val board = position.board()
        originalMove?.let { require(board.isLegal(ChessMove.fromUci(it))) { "Original move is illegal" } }
        val status = board.status()
        if (status !in listOf(PositionStatus.ONGOING, PositionStatus.CHECK))
            return EngineAnalysis(position, budget, identity, board.sideToMove, terminal = status)
        var opened: UciTransport? = null
        try {
            val transport = withTimeout(3_000) { factory.open().also { opened = it } }
            withTimeout(3_000) { handshake(transport, budget) }
            val alternatives = search(transport, position, board, budget, minOf(budget.multiPv, board.legalMoves().size), null)
            // Separate restricted-root search even when the played move was already in MultiPV.
            val original = originalMove?.let { search(transport, position, board, budget, 1, it) }
            return EngineAnalysis(position, budget, identity, board.sideToMove, alternatives, original)
        } finally {
            try {
                withContext(NonCancellable) {
                    withTimeoutOrNull(250) { opened?.send("stop"); opened?.send("quit") }
                }
            } finally { opened?.close() }
        }
    }

    private suspend fun handshake(transport: UciTransport, budget: AnalysisBudget) {
        transport.send("uci")
        var name: String? = null
        val options = mutableMapOf<String, String>()
        repeat(MAX_LINES) {
            val line = read(transport)
            if (line.startsWith("id name ")) name = line.removePrefix("id name ")
            if (line.startsWith("option name ")) {
                val option = line.removePrefix("option name ").substringBefore(" type ")
                options[option] = line.substringAfter(" default ", "").substringBefore(" min ").substringBefore(" max ")
            }
            if (line == "uciok") {
                checkProtocol(name == "${identity.name} ${identity.version}", "Engine identity does not match the pinned build")
                checkProtocol(listOf("Threads", "Hash", "MultiPV", "Ponder", "Clear Hash").all { it in options }, "Required UCI options missing")
                val networks = options.filterKeys { it == "EvalFile" || it == "EvalFileSmall" }.values.toSet()
                checkProtocol(networks == identity.networks.keys, "NNUE defaults do not match the pinned networks")
                transport.send("setoption name Threads value 1")
                transport.send("setoption name Hash value ${budget.hashMiB}")
                transport.send("setoption name Ponder value false")
                if ("UCI_LimitStrength" in options) transport.send("setoption name UCI_LimitStrength value false")
                transport.send("isready")
                waitReady(transport)
                return
            }
        }
        throw EngineProtocolException("UCI handshake output exceeds the bound")
    }

    private suspend fun waitReady(transport: UciTransport) {
        repeat(MAX_LINES) { if (read(transport) == "readyok") return }
        throw EngineProtocolException("UCI readiness output exceeds the bound")
    }

    private suspend fun search(transport: UciTransport, position: AnalysisPosition, root: BoardPosition, budget: AnalysisBudget,
                               candidates: Int, original: String?): AnalysisSearch = withTimeout(budget.moveTimeMillis.toLong() + 1_000) {
        transport.send("ucinewgame")
        transport.send("setoption name Clear Hash")
        transport.send("setoption name MultiPV value $candidates")
        transport.send("isready")
        waitReady(transport)
        transport.send(position.command())
        transport.send(budget.go(original))
        val batches = linkedMapOf<Int, MutableMap<Int, AnalyzedLine>>()
        repeat(MAX_LINES) {
            val line = read(transport)
            if (line.startsWith("info ")) parseInfo(line, root)?.let { parsed ->
                checkProtocol(parsed.rank in 1..candidates, "Unexpected MultiPV rank")
                checkProtocol(original == null || parsed.uci.first() == original, "Restricted search returned a different move")
                val batch = batches.getOrPut(parsed.depth) { mutableMapOf() }
                if (parsed.rank == 1) batch.clear()
                batch[parsed.rank] = parsed
                // Only a bounded pair of current/previous depths; never mix their candidates.
                if (batches.size > 2) batches.remove(batches.keys.min())
            }
            if (line.startsWith("bestmove ")) {
                val move = line.split(' ').getOrNull(1) ?: throw EngineProtocolException("Missing best move")
                checkProtocol(root.isLegal(ChessMove.fromUci(move)), "Engine best move is illegal")
                val batch = batches.entries.sortedByDescending { it.key }.firstOrNull { it.value[1]?.uci?.first() == move }?.value
                    ?: throw EngineProtocolException("No coherent scored line for the best move")
                val lines = batch.values.sortedBy { it.rank }
                checkProtocol(lines.map { it.uci.first() }.distinct().size == lines.size, "Duplicate candidate moves")
                return@withTimeout AnalysisSearch(lines, move, candidates, (1..candidates).all { it in batch })
            }
        }
        throw EngineProtocolException("UCI search output exceeds the bound")
    }

    private suspend fun read(transport: UciTransport): String {
        currentCoroutineContext().ensureActive()
        val line = transport.readLine() ?: throw EngineProtocolException("Engine ended before completing the protocol")
        checkProtocol(line.length <= MAX_LINE_LENGTH, "Engine output line exceeds the bound")
        return line.trim()
    }

    private suspend fun parseInfo(line: String, root: BoardPosition): AnalyzedLine? {
        val tokens = line.split(Regex("\\s+"))
        if (tokens.getOrNull(1) == "string" || "pv" !in tokens || "score" !in tokens) return null
        fun int(key: String, default: Int? = null): Int = tokens.indexOf(key).let { i ->
            if (i < 0 && default != null) default else tokens.getOrNull(i + 1)?.toIntOrNull()
                ?: throw EngineProtocolException("Malformed UCI $key")
        }
        fun long(key: String): Long = tokens.indexOf(key).let { i ->
            tokens.getOrNull(i + 1)?.toLongOrNull()?.takeIf { i >= 0 && it >= 0 }
                ?: throw EngineProtocolException("Malformed UCI $key")
        }
        val depth = int("depth"); val rank = int("multipv", 1)
        checkProtocol(depth in 1..128, "Unsupported analysis depth")
        val scoreAt = tokens.indexOf("score")
        val value = tokens.getOrNull(scoreAt + 2)?.toIntOrNull()
            ?: throw EngineProtocolException("Malformed UCI score")
        checkProtocol(value in -100_000..100_000, "Score exceeds the supported range")
        val bound = when (tokens.getOrNull(scoreAt + 3)) {
            "lowerbound" -> ScoreBound.LOWER; "upperbound" -> ScoreBound.UPPER; else -> ScoreBound.EXACT
        }
        val score = when (tokens.getOrNull(scoreAt + 1)) {
            "cp" -> EngineScore.Centipawns(value, bound)
            "mate" -> { checkProtocol(value != 0, "Mate zero in a nonterminal position"); EngineScore.Mate(value, bound) }
            else -> throw EngineProtocolException("Unsupported UCI score unit")
        }
        val pv = tokens.drop(tokens.indexOf("pv") + 1)
        checkProtocol(pv.isNotEmpty() && pv.size <= 256, "PV length exceeds the supported range")
        // Root was checked once with the complete original history. BoardPosition is
        // immutable: each PV starts from that same root without replaying history per info.
        var board = root
        val san = pv.map { token ->
            currentCoroutineContext().ensureActive()
            checkProtocol(board.status() in listOf(PositionStatus.ONGOING, PositionStatus.CHECK), "PV continues past a terminal position")
            val move = ChessMove.fromUci(token)
            val checked = board.sanAndPlay(move) // One legal transition, including castling/promotion/check.
            board = checked.position
            checked.san
        }
        return AnalyzedLine(rank, depth, long("nodes"), long("time"), score, pv, san)
    }

    private fun checkProtocol(condition: Boolean, message: String) {
        if (!condition) throw EngineProtocolException(message)
    }

    companion object {
        private const val MAX_LINES = 20_000
        private const val MAX_LINE_LENGTH = 8_192
    }
}
