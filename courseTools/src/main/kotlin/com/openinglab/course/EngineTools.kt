// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.storage.contentSha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Anything that can analyse a position; tests substitute a deterministic fake. */
fun interface Analyser {
    fun analyse(fen: String, moves: List<String>, depth: Int, multiPv: Int, searchMoves: List<String>): EngineResult
}

/** Thread-safe pool of engine processes with a persistent result cache keyed by the exact request. */
class EnginePool(private val engineDir: File, private val cacheFile: File, private val instances: Int, private val threadsEach: Int) : Analyser, AutoCloseable {
    private val codec = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, EngineResult>()
    private val free = java.util.concurrent.LinkedBlockingQueue<BuildEngine>()
    private val created = java.util.concurrent.atomic.AtomicInteger()
    private val identity = contentSha256(File(engineDir, "identity.json").readBytes())
    var hits = 0L; private set
    var misses = 0L; private set

    init {
        if (cacheFile.isFile) cacheFile.forEachLine { line ->
            if (line.isNotBlank()) runCatching { codec.decodeFromString(CacheRow.serializer(), line) }.getOrNull()?.let { cache[it.key] = it.result }
        }
    }

    @Serializable private data class CacheRow(val key: String, val result: EngineResult)

    private fun key(fen: String, moves: List<String>, depth: Int, multiPv: Int, searchMoves: List<String>) =
        contentSha256("v1|$identity|$fen|${moves.joinToString(" ")}|$depth|$multiPv|${searchMoves.sorted().joinToString(" ")}".encodeToByteArray())

    override fun analyse(fen: String, moves: List<String>, depth: Int, multiPv: Int, searchMoves: List<String>): EngineResult {
        val k = key(fen, moves, depth, multiPv, searchMoves)
        cache[k]?.let { synchronized(this) { hits++ }; return it }
        val engine = free.poll() ?: if (created.incrementAndGet() <= instances) BuildEngine(engineDir, threadsEach, 256) else free.take()
        try {
            val result = engine.analyse(fen, moves, depth, multiPv, searchMoves)
            cache[k] = result
            synchronized(this) {
                misses++
                cacheFile.parentFile.mkdirs()
                cacheFile.appendText(codec.encodeToString(CacheRow.serializer(), CacheRow(k, result)) + "\n")
            }
            return result
        } finally { free.put(engine) }
    }

    override fun close() { generateSequence { free.poll() }.forEach { it.close() } }
}

/** A position reached from the standard start by an exact move history. */
data class Line(val moves: List<String>) {
    val board: BoardPosition by lazy { moves.fold(BoardPosition.starting()) { b, uci -> b.apply(ChessMove.fromUci(uci)) } }
    operator fun plus(uci: String) = Line(moves + uci)
    val sideToMove: PieceColor get() = board.sideToMove
}

data class Threat(val moverThreat: String, val san: String, val gainCp: Int, val pvSan: List<String>)
data class Prevented(val uci: String, val san: String, val dropCp: Int, val illegalNow: Boolean, val refutationSan: List<String>)
data class MoveCheck(val uci: String, val lossCp: Int, val bestUci: String, val moveScore: Int, val bestScore: Int)

/** Engine questions used by the tree builder, fact builder and claim checker (Phase 3). */
class EngineTools(private val analyser: Analyser, val depth: Int = 20) {
    private val start = BoardPosition.START_FEN

    fun analyse(line: Line, multiPv: Int = 1, searchMoves: List<String> = emptyList(), d: Int = depth) =
        analyser.analyse(start, line.moves, d, multiPv, searchMoves)

    /** White-POV centipawns of the best line (mates mapped to ±100000-ish). */
    fun whiteEval(line: Line, d: Int = depth): Int? {
        // MultiPV 1 everywhere except the null-move "prevents" probe, so evaluations share one cached search.
        val best = analyse(line, multiPv = 1, d = d).best ?: return null
        return best.score * line.sideToMove.sign()
    }

    /** Position with the other side to move: the standard "what if you could move again" null-move probe. */
    fun passed(board: BoardPosition): String? {
        if (board.isInCheck()) return null
        val fields = board.toFen().split(' ').toMutableList()
        fields[1] = if (fields[1] == "w") "b" else "w"
        fields[3] = "-"
        val fen = fields.joinToString(" ")
        val flipped = runCatching { BoardPosition.fromFen(fen) }.getOrNull() ?: return null
        return if (flipped.isInCheck(flipped.sideToMove.opposite)) null else fen
    }

    /** What the side that just moved threatens, if the gain is at least [minGain] centipawns. */
    fun threat(after: Line, minGain: Int = 150): Threat? {
        val board = after.board
        val mover = board.sideToMove.opposite
        val fen = passed(board) ?: return null
        val current = analyse(after, multiPv = 1).best ?: return null
        val currentForMover = -current.score
        val probe = analyser.analyse(fen, emptyList(), depth, 1, emptyList()).best ?: return null
        val gain = probe.score - currentForMover
        if (gain < minGain) return null
        val pBoard = BoardPosition.fromFen(fen)
        val san = sanLine(pBoard, probe.pv.take(4))
        return Threat(probe.pv.first(), san.first(), gain, san).takeIf { mover == pBoard.sideToMove }
    }

    /** Opponent moves that were good if the mover had passed, but are illegal or clearly worse after [move]. */
    fun prevents(before: Line, move: String, minDrop: Int = 120): List<Prevented> {
        val board = before.board
        val fen = passed(board) ?: return emptyList()
        val passedResult = analyser.analyse(fen, emptyList(), depth, 5, emptyList())
        val bestPassed = passedResult.best?.score ?: return emptyList()
        val after = before + move
        val afterBoard = after.board
        val bestAfter = analyse(after).best?.score ?: return emptyList()
        val legalAfter = afterBoard.legalMoves().map { it.uci }.toSet()
        val passedBoard = BoardPosition.fromFen(fen)
        return passedResult.lines.filter { it.score >= bestPassed - 60 }.mapNotNull { candidate ->
            val uci = candidate.pv.first()
            val san = passedBoard.sanAndPlay(ChessMove.fromUci(uci)).san
            if (uci !in legalAfter) {
                // A captured or moved piece is not "prevented"; only a still-present piece that can no longer go there.
                val piece = passedBoard.pieceAt(uci.substring(0, 2))
                return@mapNotNull if (piece != null && afterBoard.pieceAt(uci.substring(0, 2)) == piece)
                    Prevented(uci, san, candidate.score - bestAfter, true, emptyList()) else null
            }
            val forced = analyse(after, 1, listOf(uci)).best ?: return@mapNotNull null
            val drop = candidate.score - forced.score
            if (forced.score <= bestAfter - minDrop && drop >= minDrop)
                Prevented(uci, san, drop, false, sanLine(afterBoard, forced.pv.take(5))) else null
        }.sortedByDescending { it.dropCp }
    }

    /** How much worse [move] is than the best move, for the side to move. */
    fun compare(before: Line, move: String): MoveCheck {
        val best = analyse(before, 1).best!!
        val forced = if (best.pv.first() == move) best else analyse(before, 1, listOf(move)).best!!
        return MoveCheck(move, best.score - forced.score, best.pv.first(), forced.score, best.score)
    }

    /** Line verdict at two depths: decided only when both agree (Phase 3 `verdict`/`stable`). */
    fun verdict(line: Line, advantage: Int = 100, equal: Int = 35): Pair<String, Int?> {
        val board = line.board
        if (board.legalMoves().isEmpty()) return (if (board.isInCheck()) (if (board.sideToMove == PieceColor.WHITE) "BLACK_BETTER" else "WHITE_BETTER") else "EQUAL") to null
        val shallow = whiteEval(line, depth - 4) ?: return "CONTINUE" to null
        val deep = whiteEval(line) ?: return "CONTINUE" to null
        val best = analyse(line, multiPv = 1).best!!
        val quiet = !board.isInCheck() && board.pieceAt(best.pv.first().substring(2, 4)) == null
        val result = when {
            shallow >= advantage && deep >= advantage -> "WHITE_BETTER"
            shallow <= -advantage && deep <= -advantage -> "BLACK_BETTER"
            kotlin.math.abs(shallow) <= equal && kotlin.math.abs(deep) <= equal && quiet -> "EQUAL"
            else -> "CONTINUE"
        }
        return result to deep.takeIf { kotlin.math.abs(it) < 50_000 }
    }

    // FEN-rooted variants for evaluation sets that do not start from the standard position.
    fun analyseFen(fen: String, d: Int = depth, multiPv: Int = 1) = analyser.analyse(fen, emptyList(), d, multiPv, emptyList())
    fun passedBoard(board: BoardPosition): BoardPosition? = passed(board)?.let(BoardPosition::fromFen)
    fun threatFen(fenAfter: String, minGain: Int = 150): Threat? {
        val board = BoardPosition.fromFen(fenAfter)
        val fen = passed(board) ?: return null
        val current = analyseFen(fenAfter).best ?: return null
        val probe = analyseFen(fen).best ?: return null
        val gain = probe.score + current.score
        if (gain < minGain) return null
        val san = sanLine(BoardPosition.fromFen(fen), probe.pv.take(4))
        return Threat(probe.pv.first(), san.first(), gain, san)
    }
    /** Centipawn loss of [uci] against the best move from [fen], for the side to move. */
    fun compareFen(fen: String, uci: String): Int {
        val best = analyseFen(fen).best!!
        if (best.pv.first() == uci) return 0
        val forced = analyser.analyse(fen, emptyList(), depth, 1, listOf(uci)).best!!
        return best.score - forced.score
    }

    fun sanLine(board: BoardPosition, ucis: List<String>): List<String> {
        var b = board
        return ucis.map { uci -> val t = b.sanAndPlay(ChessMove.fromUci(uci)); b = t.position; t.san }
    }
}
