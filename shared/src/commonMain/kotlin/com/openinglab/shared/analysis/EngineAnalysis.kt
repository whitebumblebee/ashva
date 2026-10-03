// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.analysis

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.PositionStatus
import com.openinglab.shared.model.PieceColor

/** A full move path, not a normalized opening key: clocks and repetitions affect analysis. */
data class AnalysisPosition(val initialFen: String, val moves: List<String> = emptyList()) {
    companion object { const val MAX_HISTORY_PLIES = 512 }
    init { require(moves.size <= MAX_HISTORY_PLIES) { "Analysis history exceeds the supported bound" } }
    val hasHistoryFromStart: Boolean get() = initialFen == BoardPosition.START_FEN
    fun board(): BoardPosition {
        var board = BoardPosition.fromFen(initialFen)
        moves.forEach { board = board.apply(com.openinglab.shared.model.ChessMove.fromUci(it)) }
        return board
    }
    internal fun command(): String = "position fen ${BoardPosition.fromFen(initialFen).toFen()}" +
        if (moves.isEmpty()) "" else " moves ${moves.joinToString(" ")}" // board() validates every token first.
}

/** Per-search ceiling. Alternatives and the original move each get this same ceiling. */
data class AnalysisBudget(
    val depth: Int = 16,
    val nodes: Int = 50_000,
    val moveTimeMillis: Int = 1_000,
    val multiPv: Int = 3,
    val hashMiB: Int = 16,
) {
    init {
        require(depth in 1..30 && nodes in 1..200_000 && moveTimeMillis in 1..3_000)
        require(multiPv in 1..5 && hashMiB in 1..32)
    }
    internal fun go(move: String? = null): String =
        "go depth $depth nodes $nodes movetime $moveTimeMillis" + (move?.let { " searchmoves $it" } ?: "")
}

data class EngineIdentity(
    val name: String,
    val version: String,
    val binarySha256: String,
    val networks: Map<String, String>,
) {
    init {
        require(name.isNotBlank() && version.isNotBlank())
        require(binarySha256.matches(Regex("[a-f0-9]{64}")))
        require(networks.isNotEmpty() && networks.all { (name, hash) ->
            name.matches(Regex("nn-[a-f0-9]{12}\\.nnue")) && hash.matches(Regex("[a-f0-9]{64}"))
        })
    }
}

enum class ScoreBound { EXACT, LOWER, UPPER;
    internal fun flipped() = when (this) { EXACT -> EXACT; LOWER -> UPPER; UPPER -> LOWER }
}

/** UCI scores belong to the root side to move, never implicitly to White. */
sealed interface EngineScore {
    val bound: ScoreBound
    data class Centipawns(val value: Int, override val bound: ScoreBound = ScoreBound.EXACT) : EngineScore
    data class Mate(val moves: Int, override val bound: ScoreBound = ScoreBound.EXACT) : EngineScore
    fun forSide(rootSide: PieceColor, selectedSide: PieceColor): EngineScore = if (rootSide == selectedSide) this else when (this) {
        is Centipawns -> Centipawns(-value, bound.flipped())
        is Mate -> Mate(-moves, bound.flipped())
    }
}

data class AnalyzedLine(
    val rank: Int, val depth: Int, val nodes: Long, val timeMillis: Long,
    val score: EngineScore, val uci: List<String>, val san: List<String>,
)

data class AnalysisSearch(
    val lines: List<AnalyzedLine>, val bestMove: String,
    val requestedCandidates: Int, val completeCandidateSet: Boolean,
)

enum class MoveComparison { NEAR_EQUAL_AT_BUDGET, DIFFERENT_AT_BUDGET, NOT_COMPARABLE }

data class EngineAnalysis(
    val position: AnalysisPosition, val budget: AnalysisBudget, val engine: EngineIdentity,
    val rootSide: PieceColor, val alternatives: AnalysisSearch? = null,
    val original: AnalysisSearch? = null, val terminal: PositionStatus? = null,
) {
    /** An explicitly heuristic CP comparison, not a mistake/blunder classification or proof. */
    fun compareOriginal(toleranceCp: Int = 20): MoveComparison {
        require(toleranceCp in 0..100)
        val best = alternatives?.lines?.firstOrNull()?.score as? EngineScore.Centipawns
        val played = original?.lines?.firstOrNull()?.score as? EngineScore.Centipawns
        if (best == null || played == null || best.bound != ScoreBound.EXACT || played.bound != ScoreBound.EXACT)
            return MoveComparison.NOT_COMPARABLE
        return if (kotlin.math.abs(best.value.toLong() - played.value) <= toleranceCp)
            MoveComparison.NEAR_EQUAL_AT_BUDGET else MoveComparison.DIFFERENT_AT_BUDGET
    }
    /** Equality includes the whole history, both budgets, exact engine/NNUE and original move. */
    fun cacheKey(originalMove: String?) = AnalysisCacheKey(position, budget, engine, originalMove)
}

data class AnalysisCacheKey(
    val position: AnalysisPosition, val budget: AnalysisBudget, val engine: EngineIdentity,
    val originalMove: String?, val protocolVersion: Int = 1,
)

interface ChessAnalysisEngine {
    suspend fun analyze(position: AnalysisPosition, originalMove: String? = null,
                        budget: AnalysisBudget = AnalysisBudget()): EngineAnalysis
}

/** Each analysis owns a fresh transport. Cancellation/close MUST terminate its process, not just its reader. */
interface UciTransport {
    suspend fun send(command: String)
    suspend fun readLine(): String?
    fun close()
}

fun interface UciTransportFactory { suspend fun open(): UciTransport }

class EngineProtocolException(message: String) : IllegalStateException(message)
