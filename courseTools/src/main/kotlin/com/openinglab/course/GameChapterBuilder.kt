// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.parseSanAndPlay
import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.course.CourseGame
import com.openinglab.shared.model.ChessMove
import java.io.File

@kotlinx.serialization.Serializable
data class CriticalMoment(val ply: Int, val kind: String, val playedSan: String, val branchSan: String, val lossCp: Int, val gapCp: Int)

/**
 * Phase 11: one original GM game as an immutable main line with engine branches at critical moments.
 * Moments are found by rules (loss of the played move, only-moves whose tempting alternative fails), never by
 * guessing the players' intentions.
 */
class GameChapterBuilder(private val p: CoursePipeline) {
    fun build(chapterId: String) {
        val chapter = p.config.chapters.first { it.id == chapterId && it.kind == "GAME" }
        val games = masterFile.readLines().filter { it.isNotBlank() }.map(FilteredGame::fromTsv)
        val game = chapter.gameId?.let { id -> games.first { it.id == id } } ?: games
            .filter { g -> g.san.size in 50..160 && g.result != "1/2-1/2" && (g.whiteElo ?: 0) >= 2650 && (g.blackElo ?: 0) >= 2650 &&
                g.san.size >= chapter.rootSan.size && g.san.subList(0, chapter.rootSan.size) == chapter.rootSan }
            .maxWithOrNull(compareBy<FilteredGame>({ minOf(it.whiteElo ?: 0, it.blackElo ?: 0) }, { it.date }))
            ?: error("No qualifying master game for $chapterId")
        println("Selected ${game.white} (${game.whiteElo}) – ${game.black} (${game.blackElo}), ${game.event}, ${game.date}, ${game.result}, ${game.san.size} plies, ${game.site}")
        var board = BoardPosition.starting()
        val ucis = game.san.map { san -> val t = board.parseSanAndPlay(san); board = t.position; t.move.uci }
        p.pool().use { pool ->
            val tools = EngineTools(pool, p.config.thresholds.depth)
            val nodes = mutableListOf<TreeNode>()
            var parent: String? = null
            val jobs = java.util.concurrent.Executors.newFixedThreadPool(3)
            // Analyse every position before each original move (cached, parallel).
            val analyses = try {
                ucis.indices.map { i -> jobs.submit(java.util.concurrent.Callable { tools.analyse(Line(ucis.take(i)), multiPv = 2) }) }.map { it.get() }
            } finally { jobs.shutdown() }
            val moments = mutableListOf<CriticalMoment>()
            for (i in ucis.indices) {
                val line = Line(ucis.take(i))
                val a = analyses[i]
                val best = a.best ?: break
                val playedScore = a.lines.firstOrNull { it.pv.first() == ucis[i] }?.score ?: tools.compare(line, ucis[i]).moveScore
                val loss = best.score - playedScore
                val second = a.lines.getOrNull(1)
                val gap = second?.let { best.score - it.score } ?: 0
                val b = line.board
                if (i >= 12 && loss >= 100) moments += CriticalMoment(i, "BETTER", game.san[i], b.sanAndPlay(ChessMove.fromUci(best.pv.first())).san, loss, gap)
                else if (i >= 12 && best.pv.first() == ucis[i] && second != null && gap >= 150 && kotlin.math.abs(best.score) < 400)
                    moments += CriticalMoment(i, "ALTERNATIVE", game.san[i], b.sanAndPlay(ChessMove.fromUci(second.pv.first())).san, 0, gap)
            }
            val chosen = moments.sortedByDescending { maxOf(it.lossCp, it.gapCp) }.take(6).sortedBy { it.ply }
            println("Critical moments: ${chosen.joinToString { "${it.ply / 2 + 1}${if (it.ply % 2 == 0) "." else "..."}${it.playedSan} ${it.kind} ${it.branchSan} (${maxOf(it.lossCp, it.gapCp)})" }}")
            board = BoardPosition.starting()
            for ((i, uci) in ucis.withIndex()) {
                val t = board.sanAndPlay(ChessMove.fromUci(uci))
                val node = TreeNode(TreeBuilder.idFor(ucis.take(i + 1)), parent, ucis.take(i + 1), uci, t.san, "ORIGINAL", 1.0, 0, null, 0, null)
                analyses.getOrNull(i + 1)?.best?.let { n -> node.evalCp = n.cp?.times(t.position.sideToMove.sign()); node.depth = analyses[i + 1].depth }
                nodes += node; parent = node.id; board = t.position
            }
            for (m in chosen) {
                val start = Line(ucis.take(m.ply))
                val first = start.board.parseSanAndPlay(m.branchSan).move.uci
                val branch = mutableListOf(first)
                // Continue with the engine's best play until a verdict or 12 plies.
                while (branch.size < 12) {
                    val l = start.let { Line(it.moves + branch) }
                    if (branch.size >= 4 && tools.verdict(l).first != "CONTINUE") break
                    val next = tools.analyse(l).best?.pv?.first() ?: break
                    branch += next
                }
                var bParent = nodes.first { it.moves.size == m.ply }.id.takeIf { m.ply > 0 }
                var b = start.board
                for ((j, uci) in branch.withIndex()) {
                    val moves = start.moves + branch.take(j + 1)
                    val t = b.sanAndPlay(ChessMove.fromUci(uci))
                    val node = TreeNode(TreeBuilder.idFor(moves), bParent, moves, uci, t.san, m.kind, 0.0, 0, null, 0, null)
                    if (nodes.none { it.id == node.id }) nodes += node
                    bParent = node.id; b = t.position
                }
                val leafId = TreeBuilder.idFor(start.moves + branch)
                val leaf = nodes.first { it.id == leafId }
                val (v, e) = tools.verdict(Line(leaf.moves))
                leaf.verdict = if (v == "CONTINUE") (e?.let { if (it >= 100) "WHITE_BETTER" else if (it <= -100) "BLACK_BETTER" else if (kotlin.math.abs(it) <= 35) "EQUAL" else "UNCLEAR" } ?: "UNCLEAR") else v
                leaf.verdictEval = e
                leaf.stopReason = "branch"
            }
            val tree = ChapterTree(chapter, p.config.side, 0, p.config.thresholds, nodes, 0, games.size.toLong())
            p.treeFile(chapterId).also { it.parentFile.mkdirs() }.writeText(json.encodeToString(ChapterTree.serializer(), tree))
            val meta = CourseGame(game.white, game.black, game.whiteElo, game.blackElo, game.event, game.date, game.result, game.site, "CC-BY-SA-4.0")
            File(p.dir, "game/$chapterId.meta.json").also { it.parentFile.mkdirs() }.writeText(json.encodeToString(CourseGame.serializer(), meta))
            File(p.dir, "game/$chapterId.moments.json").writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(CriticalMoment.serializer()), chosen))
            println("Game chapter $chapterId: ${nodes.size} nodes (${ucis.size} original plies, ${chosen.size} branches)")
        }
    }
}
