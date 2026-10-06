// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Per-node data the both-colour course keeps beyond [TreeNode]: names, strength bands, transpositions. */
@Serializable
data class NodeExtra(val id: String, val opening: String?, val bands: Map<String, Int>, val transposesTo: String? = null, val source: String)

/**
 * Both-colour course from the data-only variation map: every master move for either side is kept (roles MAIN/SIDE),
 * club-only moves become DEVIATION or TRAP (engine loss ≥ 1.5), and every line that is not a transposition is
 * continued with engine play until a two-depth verdict (punish lines after a TRAP).
 */
class FullCourseBuilder internal constructor(private val config: CourseConfig, private val tools: EngineTools, private val parallel: Int,
                                            private val log: (String) -> Unit, private val pipeline: CoursePipeline? = null) {
    constructor(p: CoursePipeline, tools: EngineTools, parallel: Int, log: (String) -> Unit) : this(p.config, tools, parallel, log, p)
    private val t = config.thresholds

    fun build() {
        val p = requireNotNull(pipeline)
        val map = json.decodeFromString(ListSerializer(MapNode.serializer()), File(p.dir, "map/variation-map.json").readText())
        val (all, extra) = build(map)
        // Split into chapters by variation name (move orders transpose), keeping each chapter's approach moves.
        val allById = all.associateBy { it.id }
        fun sanPath(n: TreeNode) = generateSequence(n) { it.parent?.let(allById::get) }.map { it.san }.toList().reversed()
        val chapterOf = all.associate { n ->
            val own = extra[n.id]?.opening
            n.id to (TableOfContents.chapterFor(config, sanPath(n), own) ?: "other")
        }
        for (chapter in config.chapters.filter { it.kind == "REPERTOIRE" }) {
            val members = all.filter { chapterOf[it.id] == chapter.id }
            if (members.isEmpty()) continue
            val keep = LinkedHashSet<String>()
            for (m in members) generateSequence(m) { it.parent?.let(allById::get) }.forEach { keep += it.id }
            val chapterNodes = all.filter { it.id in keep }
            val rootPly = members.minOf { it.moves.size } - 1
            // Approach moves shared with other chapters keep their own role; lines outside this chapter are dropped.
            val tree = ChapterTree(chapter, "BOTH", rootPly, t, chapterNodes, 0, 0)
            p.treeFile(chapter.id).also { it.parentFile.mkdirs() }.writeText(json.encodeToString(ChapterTree.serializer(), tree))
            File(p.dir, "extra/${chapter.id}.json").also { it.parentFile.mkdirs() }.writeText(
                json.encodeToString(ListSerializer(NodeExtra.serializer()), chapterNodes.mapNotNull { extra[it.id] }))
            log("${chapter.id}: ${chapterNodes.size} positions, ${chapterNodes.count { n -> chapterNodes.none { it.parent == n.id } }} lines")
        }
    }

    /** In-memory engine pass used by the file pipeline and synthetic fixtures. */
    internal fun build(map: List<MapNode>): Pair<List<TreeNode>, Map<String, NodeExtra>> {
        val byId = map.associateBy { it.id }
        val kids = map.groupBy { it.parent }
        fun inherited(n: MapNode): String? = generateSequence(n) { it.parent?.let(byId::get) }.firstNotNullOfOrNull { it.name }
        val nodes = ConcurrentHashMap<String, TreeNode>()
        val extra = ConcurrentHashMap<String, NodeExtra>()
        // Reach: share of club games (early) or master games (later) that follow each move, multiplied along the line.
        val reach = HashMap<String, Double>()
        for (n in map) {
            val parent = n.parent?.let(byId::get)
            val clubTotal = parent?.let { if (it.positionClubGames > 0) it.positionClubGames else it.clubGames } ?: 0
            val masterTotal = parent?.let { if (it.positionMasterGames > 0) it.positionMasterGames else it.masterGames } ?: 0
            val share = when {
                // The approach is the forced path into the course, not a sampled branch.
                n.source == "approach" || parent == null -> 1.0
                clubTotal >= 30 && n.clubGames > 0 -> n.clubGames.toDouble() / clubTotal
                masterTotal > 0 -> n.masterGames.toDouble() / masterTotal
                else -> 0.0
            }
            reach[n.id] = (parent?.let { reach[it.id] } ?: 1.0) * share.coerceIn(0.0, 1.0)
        }
        for (n in map) {
            val siblings = kids[n.parent].orEmpty()
            val role = when {
                n.source == "approach" -> "MAIN"
                n.source == "club" -> "DEVIATION"
                siblings.filter { it.source != "club" }.maxByOrNull { it.masterGames }?.id == n.id -> "MAIN"
                else -> "SIDE"
            }
            nodes[n.id] = TreeNode(n.id, n.parent, n.moves, n.moves.last(), n.san, role, reach.getValue(n.id), n.clubGames, null, n.masterGames, null,
                positionClubGames = n.positionClubGames, positionMasterGames = n.positionMasterGames)
            extra[n.id] = NodeExtra(n.id, inherited(n), n.bandGames, n.transposesTo, n.source)
        }
        // Engine: evaluate every map position, classify club-only moves, then extend open lines to a verdict.
        val pool = Executors.newFixedThreadPool(parallel)
        try {
            val leaves = map.filter { kids[it.id].isNullOrEmpty() && it.transposesTo == null }
            log("Evaluating ${map.size} map positions; extending ${leaves.size} open lines")
            map.chunked(200).forEachIndexed { i, chunk ->
                chunk.map { n -> pool.submit(Callable {
                    val node = nodes.getValue(n.id)
                    val line = Line(n.moves)
                    val a = tools.analyse(line, multiPv = 1)
                    a.best?.let { b -> node.evalCp = b.cp?.times(line.sideToMove.sign()); node.mate = b.mate?.times(line.sideToMove.sign()); node.depth = a.depth }
                    if (node.role == "DEVIATION" && tools.compare(Line(n.moves.dropLast(1)), n.moves.last()).lossCp >= t.trapLoss) {
                        node.role = "TRAP"; node.sinceTrap = 0
                    }
                }) }.forEach { it.get() }
                log("evaluated ${minOf((i + 1) * 200, map.size)}/${map.size}")
            }
            val added = ConcurrentHashMap<String, TreeNode>()
            leaves.chunked(60).forEachIndexed { i, chunk ->
                chunk.map { leaf -> pool.submit(Callable { extend(nodes.getValue(leaf.id), extra.getValue(leaf.id).opening, added, extra) }) }.forEach { it.get() }
                log("extended ${minOf((i + 1) * 60, leaves.size)}/${leaves.size} lines (${added.size} engine positions)")
            }
            nodes.putAll(added)
        } finally { pool.shutdown() }
        return nodes.values.sortedWith(compareBy({ it.moves.size }, { it.id })) to extra.toMap()
    }

    /** Continue an open line with the engine's best moves (both sides) until a decided or settled position. */
    private fun extend(leaf: TreeNode, opening: String?, out: MutableMap<String, TreeNode>, extra: MutableMap<String, NodeExtra>) {
        var current = leaf
        val punishing = leaf.role in setOf("TRAP", "PUNISH")
        for (step in 0 until 16) {
            val line = Line(current.moves)
            val board = line.board
            val (v, e) = tools.verdict(line)
            val settled = when {
                v == "CONTINUE" -> false
                punishing -> current.moves.size - leaf.moves.size >= 3
                v == "EQUAL" -> step >= 2 && developed(board)
                else -> step >= 1
            }
            if (settled || board.legalMoves().isEmpty()) { current.verdict = v.takeIf { it != "CONTINUE" } ?: classify(e); current.verdictEval = e; current.stopReason = "verdict"; return }
            val best = tools.analyse(line, multiPv = 1).best ?: run { current.verdict = classify(e); current.stopReason = "terminal"; return }
            val uci = best.pv.first()
            val t2 = board.sanAndPlay(ChessMove.fromUci(uci))
            val moves = current.moves + uci
            val node = TreeNode(TreeBuilder.idFor(moves), current.id, moves, uci, t2.san, if (punishing) "PUNISH" else "ENGINE", current.reach, 0, null, 0, null,
                sinceTrap = if (punishing) step + 1 else -1)
            node.evalCp = best.cp?.times(board.sideToMove.sign())
            out[node.id] = node
            extra[node.id] = NodeExtra(node.id, opening, emptyMap(), null, "engine")
            current = node
        }
        val (v, e) = tools.verdict(Line(current.moves))
        current.verdict = v.takeIf { it != "CONTINUE" } ?: classify(e); current.verdictEval = e; current.stopReason = "cap"
    }

    private fun classify(e: Int?) = when { e == null -> "UNCLEAR"; e >= 100 -> "WHITE_BETTER"; e <= -100 -> "BLACK_BETTER"; kotlin.math.abs(e) <= 35 -> "EQUAL"; else -> "UNCLEAR" }

    private fun developed(board: BoardPosition): Boolean = PieceColor.entries.all { side ->
        val home = if (side == PieceColor.WHITE) listOf("b1", "c1", "f1", "g1") else listOf("b8", "c8", "f8", "g8")
        home.count { sq -> board.pieceAt(sq)?.let { it.color == side && it.type in listOf(com.openinglab.shared.model.PieceType.KNIGHT, com.openinglab.shared.model.PieceType.BISHOP) } == true } <= 1
    }
}
