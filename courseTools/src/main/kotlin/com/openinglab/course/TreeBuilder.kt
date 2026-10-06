// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.parseSanAndPlay
import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import kotlinx.serialization.Serializable
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@Serializable
data class Thresholds(
    val clubShare: Double = 0.03,
    val clubMinPosition: Int = 30,
    val clubMinMove: Int = 8,
    val masterMinMove: Int = 5,
    val masterMinPosition: Int = 10,
    val engineTop: Int = 3,
    val engineMargin: Int = 30,
    val learnerMargin: Int = 30,
    val trapLoss: Int = 150,
    /** "Equal" may end a line only this many plies into the chapter and once both sides have developed. */
    val minEqualPlies: Int = 16,
    val reachCutoff: Double = 0.001,
    val maxChapterPlies: Int = 40,
    val depth: Int = 22,
)

@Serializable
data class ChapterConfig(val id: String, val title: String, val rootSan: List<String>, val kind: String = "REPERTOIRE", val gameId: String? = null)

@Serializable
data class CourseConfig(
    val id: String, val version: Int, val title: String, val side: String, val level: String, val summary: String,
    val chapters: List<ChapterConfig>, val thresholds: Thresholds = Thresholds(),
    /** Learner-move overrides keyed by the exact UCI history (space separated) → forced UCI move. */
    val overrides: Map<String, String> = emptyMap(),
    /** Ordered "name contains" → chapter rules; variation names decide chapters before move-order prefixes. */
    val chapterNames: Map<String, String> = emptyMap(),
)

@Serializable
data class TreeNode(
    val id: String,
    val parent: String?,
    val moves: List<String>,
    val uci: String,
    val san: String,
    var role: String,
    val reach: Double,
    val clubGames: Int,
    val clubScore: Double?,
    val masterGames: Int,
    val masterScore: Double?,
    var evalCp: Int? = null,
    var mate: Int? = null,
    var depth: Int = 0,
    var verdict: String? = null,
    var verdictEval: Int? = null,
    var stopReason: String? = null,
    var positionClubGames: Int = 0,
    var positionMasterGames: Int = 0,
    /** Plies since the opponent's mistake on punish lines (0 on the mistake itself). */
    var sinceTrap: Int = -1,
)

@Serializable
data class ChapterTree(val chapter: ChapterConfig, val side: String, val rootPly: Int, val thresholds: Thresholds, val nodes: List<TreeNode>,
                       val clubGames: Long, val masterGames: Long) {
    fun children(id: String) = nodes.filter { it.parent == id }
    val leaves: List<TreeNode> get() = nodes.filter { n -> nodes.none { it.parent == n.id } }
}

/**
 * Phase 5: deterministic line tree. Learner moves: one engine-sound move chosen by practical results.
 * Opponent moves: everything common at club level, played by masters, strong for the engine, or a common mistake
 * (continued as a punish line). Lines stop at a two-depth engine verdict once past main theory, below the reach
 * cutoff, or at the reported ply cap.
 */
class TreeBuilder(
    private val tools: EngineTools,
    private val club: PositionIndex,
    private val master: PositionIndex,
    private val config: CourseConfig,
    private val parallel: Int,
    private val log: (String) -> Unit = {},
) {
    private val t = config.thresholds
    private val learner = PieceColor.valueOf(config.side)

    fun build(chapter: ChapterConfig): ChapterTree {
        val nodes = mutableListOf<TreeNode>()
        var board = BoardPosition.starting()
        var parent: String? = null
        val prefix = mutableListOf<String>()
        for (san in chapter.rootSan) {
            val key = board.positionKey
            val tr = board.parseSanAndPlay(san)
            prefix += tr.move.uci
            val node = node(parent, prefix.toList(), tr.san, "MAIN", 1.0, key, board.sideToMove == PieceColor.WHITE)
            nodes += node; parent = node.id; board = tr.position
        }
        val rootPly = chapter.rootSan.size
        var frontier = listOf(nodes.last())
        val pool = Executors.newFixedThreadPool(parallel)
        try {
            while (frontier.isNotEmpty()) {
                log("${chapter.id}: expanding ${frontier.size} positions at ply ${frontier.first().moves.size} (${nodes.size} nodes)")
                val expanded = frontier.map { n -> pool.submit(Callable { expand(n, rootPly) }) }.map { it.get() }
                frontier = expanded.flatten()
                nodes += frontier
            }
        } finally { pool.shutdown() }
        return ChapterTree(chapter, config.side, rootPly, t, nodes.toList(), club.games, master.games)
    }

    private fun node(parent: String?, moves: List<String>, san: String, role: String, reach: Double, beforeKey: String, whiteMoved: Boolean, sinceTrap: Int = -1): TreeNode {
        val uci = moves.last()
        val c = club.movesAt(beforeKey)[uci]
        val m = master.movesAt(beforeKey)[uci]
        return TreeNode(id = idFor(moves), parent = parent, moves = moves, uci = uci, san = san, role = role, reach = reach,
            clubGames = c?.games ?: 0, clubScore = c?.takeIf { it.games > 0 }?.scoreFor(whiteMoved),
            masterGames = m?.games ?: 0, masterScore = m?.takeIf { it.games > 0 }?.scoreFor(whiteMoved), sinceTrap = sinceTrap)
    }

    private fun lineage(n: TreeNode, roles: Set<String>): Boolean = n.role in roles

    /** Expands one position; mutates [n] with its evaluation/verdict and returns its children. */
    private fun expand(n: TreeNode, rootPly: Int): List<TreeNode> {
        val line = Line(n.moves)
        val board = line.board
        val key = board.positionKey
        val clubMoves = club.movesAt(key); val clubN = clubMoves.values.sumOf { it.games }
        val masterMoves = master.movesAt(key); val masterN = masterMoves.values.sumOf { it.games }
        n.positionClubGames = clubN; n.positionMasterGames = masterN
        val toMove = board.sideToMove
        val analysis = tools.analyse(line, multiPv = 5)
        val best = analysis.best
        if (best == null) { // mate or stalemate on the board
            n.verdict = tools.verdict(line).first; n.stopReason = "terminal"; return emptyList()
        }
        n.evalCp = best.cp?.times(toMove.sign()); n.mate = best.mate?.times(toMove.sign()); n.depth = analysis.depth
        val chapterPly = n.moves.size - rootPly
        val punishing = n.role in setOf("TRAP", "PUNISH")
        if (chapterPly >= 1) {
            val pastTheory = clubN < t.clubMinPosition && masterN < t.masterMinPosition
            if (chapterPly >= t.maxChapterPlies) return stop(n, line, "cap")
            val learnerBetter = if (learner == PieceColor.WHITE) "WHITE_BETTER" else "BLACK_BETTER"
            val (v, e) = if (pastTheory || punishing || toMove == learner) tools.verdict(line) else "CONTINUE" to null
            if (!punishing && toMove == learner && v == learnerBetter && n.role in setOf("MAIN", "DEVIATION")) {
                // The opponent's move just handed over a clear advantage: teach the punishment instead of stopping.
                n.role = "TRAP"
                n.sinceTrap = 0
                return expand(n, rootPly)
            }
            if (pastTheory || punishing) {
                val settled = when {
                    punishing -> n.sinceTrap >= 3 && toMove != learner
                    v == "EQUAL" -> chapterPly >= t.minEqualPlies && developed(board)
                    else -> true
                }
                if (v != "CONTINUE" && settled) { n.verdict = v; n.verdictEval = e; n.stopReason = "verdict"; return emptyList() }
            }
            // A line never ends on an opponent move: the learner always gets an answer first.
            if (n.reach < t.reachCutoff && toMove != learner) return stop(n, line, "reach")
        }
        val whiteMoves = toMove == PieceColor.WHITE
        val childRole = { base: String -> if (punishing) "PUNISH" else base }
        return if (toMove == learner) {
            val bestScore = best.score
            val sound = analysis.lines.filter { it.score >= bestScore - t.learnerMargin }.map { it.pv.first() }
            val override = config.overrides[n.moves.joinToString(" ")]
            val practical = sound.filter { (clubMoves[it]?.games ?: 0) >= t.clubMinMove || (masterMoves[it]?.games ?: 0) >= 3 }
            // Prefer what strong players actually choose; results decide only between well-sampled moves.
            val wellSampled = practical.filter { (clubMoves[it]?.games ?: 0) >= 100 }
            val choice = override ?: (if (wellSampled.size >= 2) wellSampled.maxByOrNull { clubMoves.getValue(it).scoreFor(whiteMoves) }
                else practical.maxByOrNull { uci -> (masterMoves[uci]?.games ?: 0) * 3 + (clubMoves[uci]?.games ?: 0) - sound.indexOf(uci) * 0.01 })
                ?: sound.first()
            val san = board.sanAndPlay(ChessMove.fromUci(choice)).san
            listOf(node(n.id, n.moves + choice, san, childRole("MAIN"), n.reach, key, whiteMoves, if (punishing) n.sinceTrap + 1 else -1))
        } else {
            val enoughClub = clubN >= t.clubMinPosition
            val enoughMaster = masterN >= t.masterMinPosition
            if (!enoughClub && !enoughMaster || punishing) {
                // Beyond the data (or demonstrating a punishment): the opponent's best defence only.
                val choice = if (punishing && enoughClub) clubMoves.maxBy { it.value.games }.key
                    .takeIf { uci -> tools.compare(line, uci).lossCp < t.trapLoss } ?: best.pv.first() else best.pv.first()
                val san = board.sanAndPlay(ChessMove.fromUci(choice)).san
                return listOf(node(n.id, n.moves + choice, san, childRole("MAIN"), n.reach, key, whiteMoves, if (punishing) n.sinceTrap + 1 else -1))
            }
            val candidates = linkedSetOf<String>()
            if (enoughClub) clubMoves.filter { it.value.games >= t.clubMinMove && it.value.games.toDouble() / clubN >= t.clubShare }.keys.forEach { candidates += it }
            masterMoves.filter { it.value.games >= t.masterMinMove }.keys.forEach { candidates += it }
            analysis.lines.take(t.engineTop).filter { it.score >= best.score - t.engineMargin }.forEach { candidates += it.pv.first() }
            val scored = candidates.map { uci ->
                val loss = analysis.lines.firstOrNull { it.pv.first() == uci }?.let { best.score - it.score } ?: tools.compare(line, uci).lossCp
                val share = when {
                    enoughClub -> (clubMoves[uci]?.games ?: 0).toDouble() / clubN
                    else -> (masterMoves[uci]?.games ?: 0).toDouble() / masterN
                }
                Triple(uci, loss, share)
            }
            val soundMoves = scored.filter { it.second < t.trapLoss }
            val traps = scored.filter { it.second >= t.trapLoss && it.third >= t.clubShare && enoughClub }
            val mainUci = soundMoves.maxByOrNull { it.third * 1000 - it.second }?.first ?: best.pv.first()
            val chosen = (soundMoves.map { it.first to if (it.first == mainUci) "MAIN" else "DEVIATION" } + traps.map { it.first to "TRAP" })
                .ifEmpty { listOf(best.pv.first() to "MAIN") }
            chosen.map { (uci, role) ->
                val share = scored.firstOrNull { it.first == uci }?.third ?: 0.0
                // Engine-only candidates without game data keep the parent reach only on the main path.
                val reach = if (share > 0) n.reach * share else if (role == "MAIN") n.reach else n.reach * t.reachCutoff
                val san = board.sanAndPlay(ChessMove.fromUci(uci)).san
                node(n.id, n.moves + uci, san, role, reach, key, whiteMoves, if (role == "TRAP") 0 else -1)
            }
        }
    }

    private fun developed(board: BoardPosition): Boolean = PieceColor.entries.all { side ->
        val home = if (side == PieceColor.WHITE) listOf("b1", "c1", "f1", "g1") else listOf("b8", "c8", "f8", "g8")
        home.count { sq -> board.pieceAt(sq)?.let { it.color == side && it.type in listOf(com.openinglab.shared.model.PieceType.KNIGHT, com.openinglab.shared.model.PieceType.BISHOP) } == true } <= 1
    }

    private fun stop(n: TreeNode, line: Line, reason: String): List<TreeNode> {
        val e = tools.whiteEval(line)
        n.verdict = when {
            e == null -> "UNCLEAR"; e >= 100 -> "WHITE_BETTER"; e <= -100 -> "BLACK_BETTER"; kotlin.math.abs(e) <= 35 -> "EQUAL"; else -> "UNCLEAR"
        }
        n.verdictEval = e?.takeIf { kotlin.math.abs(it) < 50_000 }
        n.stopReason = reason
        return emptyList()
    }

    companion object {
        fun idFor(moves: List<String>): String = "n" + com.openinglab.shared.storage.contentSha256(moves.joinToString(" ").encodeToByteArray()).take(12)
    }
}
