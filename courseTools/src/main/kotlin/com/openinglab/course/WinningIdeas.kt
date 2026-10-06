// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.parseSanAndPlay
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** A normalized move feature the winning side chose more often, with its most frequent winning SAN for display. */
@Serializable
data class WinPattern(val move: String, val inWins: Int, val wins: Int, val inOthers: Int, val others: Int, val san: String = "") {
    val winShare get() = inWins.toDouble() / maxOf(1, wins)
    val otherShare get() = inOthers.toDouble() / maxOf(1, others)
}

@Serializable
data class ExampleGameRef(val white: String, val black: String, val whiteElo: Int?, val blackElo: Int?, val event: String,
                          val date: String, val result: String, val site: String, val uci: List<String>, val anchorPly: Int)

@Serializable
data class SideWins(val wins: Int, val patterns: List<WinPattern>, val examples: List<ExampleGameRef>)

@Serializable
data class VariationIdeas(val name: String, val anchorId: String, val path: List<String>, val games: Int,
                          val whiteWins: Int, val draws: Int, val blackWins: Int, val bands: Map<String, Int>,
                          val white: SideWins, val black: SideWins)

data class IdeaAnchor(val chapter: String, val name: String, val nodeId: String, val path: List<String>,
                      val key: String, val bands: Map<String, Int>)

/**
 * "How games are won" from decisive master games through each variation's position (transpositions included):
 * for each side, moves the winner played within the next [window] plies clearly more often than in that side's
 * draws and losses, excluding each side's first move (the sub-variation choice), plus strong decisive example
 * games, preferring over-the-board-like scores. Pure game statistics; nothing is guessed.
 */
object WinningIdeas {
    fun build(p: CoursePipeline, window: Int = 30, minWins: Int = 8): Map<String, List<VariationIdeas>> {
        val toc = json.decodeFromString(ListSerializer(TocChapter.serializer()), File(p.dir, "map/toc.json").readText())
        val map = json.decodeFromString(ListSerializer(MapNode.serializer()), File(p.dir, "map/variation-map.json").readText())
        val bySan = HashMap<String, MapNode>()
        run {
            val byId = map.associateBy { it.id }
            for (n in map) bySan[generateSequence(n) { it.parent?.let(byId::get) }.map { it.san }.toList().reversed().joinToString(" ")] = n
        }
        // Anchor positions: every variation listed in the table of contents.
        val anchors = toc.flatMap { c -> c.variations.mapNotNull { v ->
            val node = bySan[v.path] ?: return@mapNotNull null
            if (v.masterGames < 20) return@mapNotNull null
            var b = BoardPosition.starting(); v.path.split(' ').forEach { b = b.parseSanAndPlay(it).position }
            IdeaAnchor(c.id, v.name, node.id, v.path.split(' '), b.positionKey, v.bandGames)
        } }
        val out = masterFile.useLines { rows -> fromGames(rows.filter { it.isNotBlank() }.map(FilteredGame::fromTsv), anchors, window, minWins) }
        File(p.dir, "ideas").mkdirs()
        out.forEach { (chapter, list) -> File(p.dir, "ideas/$chapter.json").writeText(json.encodeToString(ListSerializer(VariationIdeas.serializer()), list)) }
        return out
    }

    /** Pure statistics extraction; anchors are normalized positions, so different move orders match. */
    fun fromGames(games: List<FilteredGame>, anchors: List<IdeaAnchor>, window: Int = 30, minWins: Int = 8): Map<String, List<VariationIdeas>> =
        fromGames(games.asSequence(), anchors, window, minWins)

    private fun fromGames(games: Sequence<FilteredGame>, anchors: List<IdeaAnchor>, window: Int, minWins: Int): Map<String, List<VariationIdeas>> {
        val byKey = anchors.groupBy { it.key }
        data class Hit(val game: FilteredGame, val ply: Int, val sans: List<String>)
        val hits = HashMap<String, MutableList<Hit>>()
        for (g in games) {
            var b = BoardPosition.starting()
            val sans = ArrayList<String>()
            val seen = HashSet<String>()
            try {
                for ((i, token) in g.san.withIndex()) {
                    if (i > 0) byKey[b.positionKey]?.forEach { a -> if (seen.add(a.key)) hits.getOrPut(a.key) { mutableListOf() } += Hit(g, i, sans) }
                    val t = b.parseSanAndPlay(token); sans += t.san; b = t.position
                }
            } catch (_: IllegalArgumentException) { }
        }
        fun side(hitsFor: List<Hit>, white: Boolean, ancestorExamples: Set<String>, selectedExamples: MutableSet<String>): SideWins {
            val win = if (white) "1-0" else "0-1"
            val wins = hitsFor.filter { it.game.result == win }
            // Compare only games that really continue past the variation: short agreed draws would otherwise make
            // every later move look like a "winning" move simply because decisive games last longer.
            val longWins = wins.filter { it.sans.size >= it.ply + MIN_CONTINUATION }
            val longOthers = hitsFor.filter { it.game.result != win && it.sans.size >= it.ply + MIN_CONTINUATION }
            fun moves(h: Hit): List<String> = h.sans.drop(h.ply).take(window)
                .filterIndexed { i, _ -> ((h.ply + i) % 2 == 0) == white }.drop(1)
            val winningMoves = longWins.map { moves(it) }
            val inWins = winningMoves.flatMap { it.map(WinFeatures::feature).toSet() }.groupingBy { it }.eachCount()
            val inOthers = longOthers.flatMap { moves(it).map(WinFeatures::feature).toSet() }.groupingBy { it }.eachCount()
            val representativeSans = winningMoves.flatten().groupBy(WinFeatures::feature).mapValues { (_, sans) ->
                sans.map { it.trimEnd('+', '#') }.groupingBy { it }.eachCount().entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).first().key
            }
            val patterns = if (longWins.size < minWins) emptyList() else inWins.entries.map { (m, c) ->
                WinPattern(m, c, longWins.size, inOthers[m] ?: 0, longOthers.size, representativeSans.getValue(m)) }
                .filter {
                    val others = maxOf(1, it.others)
                    it.inWins >= 5 && it.inWins.toLong() * 4 >= it.wins &&
                        (it.inWins.toLong() * others - it.inOthers.toLong() * it.wins) * 10 >= it.wins.toLong() * others
                }
                .sortedByDescending { it.winShare - it.otherShare }.take(12)
            val candidates = wins.sortedWith(compareBy<Hit> { h ->
                listOf("chess.com", "lichess", "chess24").any { h.game.site.contains(it, ignoreCase = true) } ||
                    (h.game.whiteElo ?: 0) > 2900 || (h.game.blackElo ?: 0) > 2900
            }.thenByDescending { minOf(it.game.whiteElo ?: 0, it.game.blackElo ?: 0) }
                .thenByDescending { it.sans.size >= it.ply + 30 })
                .distinctBy { it.game.id }.mapNotNull { h ->
                    // Broadcast scores can contain a transcription error late in the game: keep only the legal prefix.
                    var b = BoardPosition.starting()
                    val uci = ArrayList<String>()
                    for (s in h.game.san.take(200)) { val t = runCatching { b.parseSanAndPlay(s) }.getOrNull() ?: break; b = t.position; uci += t.move.uci }
                    if (uci.size < minOf(h.game.san.size, h.ply + 20)) null
                    else h.game.id to ExampleGameRef(h.game.white, h.game.black, h.game.whiteElo, h.game.blackElo, h.game.event, h.game.date, h.game.result, h.game.site, uci, h.ply)
                }
            val selected = candidates.filter { it.first !in ancestorExamples }.ifEmpty { candidates }.take(2)
            selectedExamples += selected.map { it.first }
            val examples = selected.map { it.second }
            return SideWins(wins.size, patterns, examples)
        }
        val selectedByAnchor = HashMap<IdeaAnchor, Set<String>>()
        val ideasByAnchor = HashMap<IdeaAnchor, VariationIdeas>()
        // Parents must choose first even when anchors arrive in table-of-contents order across chapters.
        for (a in anchors.sortedBy { it.path.size }) {
            val ancestorExamples = selectedByAnchor.filterKeys { parent ->
                parent.path.size < a.path.size && a.path.take(parent.path.size) == parent.path
            }.values.flatten().toSet()
            val selected = mutableSetOf<String>()
            val h = hits[a.key].orEmpty()
            ideasByAnchor[a] = VariationIdeas(a.name, a.nodeId, a.path, h.size, h.count { it.game.result == "1-0" }, h.count { it.game.result == "1/2-1/2" },
                h.count { it.game.result == "0-1" }, a.bands, side(h, true, ancestorExamples, selected), side(h, false, ancestorExamples, selected))
            selectedByAnchor[a] = selected
        }
        return anchors.groupBy { it.chapter }.mapValues { (_, list) -> list.map { ideasByAnchor.getValue(it) } }
    }
}

/** Games must continue this many plies after the variation to take part in the winners-versus-others comparison. */
const val MIN_CONTINUATION = 20

object WinFeatures {
    /** Piece + destination (pawn captures keep their file), so "Nxf5" and "Nf5" count as the same idea. */
    fun feature(san: String): String = san.trimEnd('+', '#', '!', '?').replace(Regex("=.*$"), "").replace("x", "").let { s ->
        if (s.startsWith("O-O")) s else if (s[0].isUpperCase()) s[0] + s.takeLast(2) else s.takeLast(2).let { d -> if (s.length > 2 && s[0] != d[0]) "${s[0]}x$d" else d }
    }
}
