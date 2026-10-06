// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import kotlinx.serialization.Serializable
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@Serializable
data class PreventedFact(val san: String, val dropCp: Int, val illegalNow: Boolean, val refutation: List<String>)

@Serializable
data class EdgeFacts(
    val nodeId: String,
    val threat: String? = null,
    val threatGainCp: Int? = null,
    val threatLine: List<String> = emptyList(),
    val prevents: List<PreventedFact> = emptyList(),
    /** Loss of this node's move compared with the engine's best move at the parent position. */
    val lossCp: Int = 0,
    val bestSan: String? = null,
    /** Engine continuation after this move (SAN), for auto plans and verdict text. */
    val pvAfter: List<String> = emptyList(),
    val evalAfter: Int? = null,
)

/** Phase 3 facts for every tree edge, computed in parallel and cached through the engine pool. */
object FactBuilder {
    fun build(tree: ChapterTree, tools: EngineTools, parallel: Int, log: (String) -> Unit, heavy: (TreeNode) -> Boolean = { true }): List<EdgeFacts> {
        val pool = Executors.newFixedThreadPool(parallel)
        try {
            val jobs = tree.nodes.map { node -> pool.submit(Callable { facts(node, tools, heavy(node)) }) }
            return jobs.mapIndexed { i, job -> job.get().also { if ((i + 1) % 50 == 0) log("facts ${i + 1}/${jobs.size}") } }
        } finally { pool.shutdown() }
    }

    /** [heavy] adds the null-move threat and "prevents" probes; light facts reuse cached searches only. */
    fun facts(node: TreeNode, tools: EngineTools, heavy: Boolean = true): EdgeFacts {
        val before = Line(node.moves.dropLast(1))
        val after = Line(node.moves)
        val threat = if (heavy) tools.threat(after) else null
        val prevents = if (heavy) tools.prevents(before, node.uci).take(3).map { PreventedFact(it.san, it.dropCp, it.illegalNow, it.refutationSan) } else emptyList()
        val check = tools.compare(before, node.uci)
        val bestSan = before.board.sanAndPlay(ChessMove.fromUci(check.bestUci)).san
        val pv = tools.analyse(after, multiPv = 1).best?.pv.orEmpty().take(12)
        return EdgeFacts(node.id, threat?.san, threat?.gainCp, threat?.pvSan.orEmpty(), prevents, check.lossCp,
            bestSan.takeIf { check.bestUci != node.uci }, tools.sanLine(after.board, pv), tools.whiteEval(after))
    }
}

/** Auto claims built only from computed facts; they still pass through the checker. */
object AutoClaims {
    private fun name(c: PieceColor) = if (c == PieceColor.WHITE) "White" else "Black"
    private fun pawns(cp: Int) = String.format(java.util.Locale.ROOT, "%.1f", kotlin.math.abs(cp) / 100.0)

    fun forNode(node: TreeNode, facts: EdgeFacts, mover: PieceColor, learner: PieceColor, clubN: Int, masterN: Int, withStats: Boolean = true): List<Claim> {
        val out = mutableListOf<Claim>()
        fun mv(san: String, side: PieceColor) = if (side == PieceColor.BLACK) "...$san" else san
        if (node.role == "TRAP" && facts.bestSan != null) {
            val reply = facts.pvAfter.firstOrNull()
            out += Claim("MISTAKE", "${mv(node.san, mover)} is a common mistake here: it loses about ${pawns(facts.lossCp)} pawns" +
                (reply?.let { " after ${mv(it, mover.opposite)}" } ?: "") + ". ${mv(facts.bestSan, mover)} was the better move.", from = "before",
                moves = listOfNotNull(node.san, reply), alternative = facts.bestSan)
        }
        // Only forcing threats (capture, check, promotion) are stated; a piece merely escaping is not a threat.
        facts.threat?.takeIf { t -> 'x' in t || t.endsWith('+') || t.endsWith('#') || '=' in t }?.let { t ->
            out += Claim("THREAT", "${name(mover)} now threatens ${mv(t, mover)}.", moves = listOf(t))
        }
        // After a capture the opponent must usually recapture, so "everything else is prevented" teaches nothing.
        facts.prevents.firstOrNull { it.dropCp >= 150 && !it.illegalNow }?.takeIf { 'x' !in node.san }?.let { p ->
            val reply = p.refutation.getOrNull(1)
            out += Claim("PREVENTS", "It also takes away ${mv(p.san, mover.opposite)}" +
                (reply?.let { ": that move would now be answered by ${mv(it, mover)}" } ?: "") + ".",
                target = p.san, moves = listOfNotNull(reply))
        }
        if (!withStats) return out
        if (node.clubGames >= 20 && clubN >= 30) {
            val share = node.clubGames.toDouble() / clubN
            val floor = (kotlin.math.floor(share * 20) / 20).coerceAtLeast(0.05)
            out += Claim("STATISTIC", "In 1600–2200 club games this was chosen in about ${(share * 100).toInt()}% of games from this position" +
                (node.clubScore?.let { ", scoring ${(it * 100).toInt()}% for ${name(mover)}" } ?: "") + ".",
                target = node.san, stat = "SHARE_ABOVE:$floor", population = "club")
        } else if (node.masterGames >= 5 && masterN >= 20) {
            out += Claim("STATISTIC", "Masters (2400+) have played it in ${node.masterGames} of the ${masterN} games in our sample from this position.",
                target = node.san, stat = "SHARE_ABOVE:${((node.masterGames * 1000L / masterN) / 1000.0)}", population = "master")
        }
        return out
    }

    fun evaluation(verdict: String, evalCp: Int?): Claim? {
        val (result, text) = when (verdict) {
            "WHITE_BETTER" -> "WHITE_BETTER" to "The engine rates this position as clearly better for White"
            "BLACK_BETTER" -> "BLACK_BETTER" to "The engine rates this position as clearly better for Black"
            "EQUAL" -> "EQUAL" to "The engine rates this position as equal"
            else -> return null
        }
        return Claim("EVALUATION", text + (evalCp?.let { " (${com.openinglab.shared.course.DeepCourseCatalog.formatEval(it)})" } ?: "") + ".", result = result)
    }
}
