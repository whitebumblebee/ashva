// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.parseSanAndPlay
import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.content.OpeningRecord
import com.openinglab.shared.model.ChessMove
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Named positions from the reviewed CC0 taxonomy pack, keyed by normalized position (transpositions match). */
class TaxonomyNames(records: List<OpeningRecord>) {
    /** Most specific name per position: the longest route, then the longest name. */
    val byPosition: Map<String, String> = records.groupBy { it.positionKey }
        .mapValues { (_, r) -> r.maxWith(compareBy<OpeningRecord>({ it.uci.size }, { it.name.length })).name }
    /** Every position on any named route, so moves along named lines are always kept. */
    val onNamedRoute: Set<String> = records.flatMap { rec ->
        var b = BoardPosition.starting()
        listOf(b.positionKey) + rec.uci.map { b = b.apply(ChessMove.fromUci(it)); b.positionKey }
    }.toSet()
    val routeEdges: Set<Pair<String, String>> = records.flatMap { rec ->
        var b = BoardPosition.starting()
        rec.uci.map { u -> val from = b.positionKey; b = b.apply(ChessMove.fromUci(u)); from to b.positionKey }
    }.toSet()

    companion object {
        fun load(family: String): TaxonomyNames {
            val codec = Json { ignoreUnknownKeys = true }
            val file = File("content/packs/lichess-openings-c67912be581f-import-v1/openings.jsonl")
            val records = file.readLines().filter { it.isNotBlank() }.map { codec.decodeFromString(OpeningRecord.serializer(), it) }
                .filter { it.family == family }
            return TaxonomyNames(records)
        }
        /** "Ruy Lopez: Morphy Defense, Closed" → "Morphy Defense, Closed". */
        fun short(name: String) = name.substringAfter(": ", name)
    }
}

/** Strength bands of the people who chose a move. Master bands use the lower of the two ratings. */
enum class Band(val label: String) { ELITE("2600+"), GM_IM("2400–2599"), MASTER("2200–2399"), CLUB("Lichess 1600–2200") }

fun masterBand(game: FilteredGame): Band? {
    val low = minOf(game.whiteElo ?: return null, game.blackElo ?: return null)
    return when { low >= 2600 -> Band.ELITE; low >= 2400 -> Band.GM_IM; low >= 2200 -> Band.MASTER; else -> null }
}

class BandedIndex(val bands: Map<Band, PositionIndex>) {
    fun stat(band: Band, key: String, uci: String) = bands[band]?.movesAt(key)?.get(uci)
    fun masterGames(key: String) = listOf(Band.ELITE, Band.GM_IM, Band.MASTER).sumOf { bands[it]?.total(key) ?: 0 }
    fun masterMoves(key: String): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (b in listOf(Band.ELITE, Band.GM_IM, Band.MASTER)) bands[b]?.movesAt(key)?.forEach { (u, s) -> out[u] = (out[u] ?: 0) + s.games }
        return out
    }
    fun clubGames(key: String) = bands[Band.CLUB]?.total(key) ?: 0
    fun clubMoves(key: String) = bands[Band.CLUB]?.movesAt(key).orEmpty().mapValues { it.value.games }

    companion object {
        fun build(prefix: List<String>, maxPlies: Int): BandedIndex {
            val dir = File(cacheDir, "filtered/bands").also { it.mkdirs() }
            val split = Band.entries.filter { it != Band.CLUB }.associateWith { File(dir, "master-${it.name.lowercase()}.tsv") }
            val writers = split.mapValues { it.value.bufferedWriter() }
            try {
                masterFile.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine
                    masterBand(FilteredGame.fromTsv(line))?.let { writers.getValue(it).apply { write(line); newLine() } }
                }
            } finally { writers.values.forEach { it.close() } }
            val bands = split.mapValues { (b, f) -> PositionIndex.build(b.name, listOf(f), prefix, maxPlies) } +
                (Band.CLUB to PositionIndex.build("CLUB", listOf(clubFile), prefix, 60))
            return BandedIndex(bands)
        }
    }
}

@Serializable
data class MapNode(
    val id: String, val parent: String?, val moves: List<String>, val san: String,
    val name: String?, val named: Boolean, val masterGames: Int, val clubGames: Int,
    val bandGames: Map<String, Int>, val source: String,
    var positionMasterGames: Int = 0, var positionClubGames: Int = 0,
    /** Set when this move order reaches a position already expanded through another route. */
    var transposesTo: String? = null,
)

@Serializable
data class MapRules(
    val minMasterMove: Int = 10, val minMasterShare: Double = 0.05, val minMasterPosition: Int = 20,
    /** Below the branching threshold, keep following the most played master move while it has this many games. */
    val mainLineGames: Int = 3,
    val clubMoveGames: Int = 100, val clubMoveShare: Double = 0.10, val maxPlies: Int = 80, val maxNodes: Int = 40_000,
)

/**
 * Data-only variation map (no engine): every move masters play for both sides, every move on a named taxonomy
 * line, and moves club players choose often. Expansion continues while master games (or a named line) continue.
 */
class VariationMapBuilder(private val index: BandedIndex, private val names: TaxonomyNames, private val rules: MapRules) {
    fun build(rootSan: List<String>): List<MapNode> {
        val nodes = ArrayList<MapNode>()
        var b = BoardPosition.starting()
        var parent: String? = null
        val prefix = ArrayList<String>()
        for (san in rootSan) {
            val key = b.positionKey
            val t = b.parseSanAndPlay(san); prefix += t.move.uci
            val node = node(parent, prefix.toList(), t.san, key, t.position, "approach"); nodes += node; parent = node.id; b = t.position
        }
        val queue = ArrayDeque(listOf(nodes.last() to b))
        // Each position is expanded once; other move orders into it end as transpositions.
        val expandedAt = HashMap<String, String>().apply { put(b.positionKey, nodes.last().id) }
        while (queue.isNotEmpty() && nodes.size < rules.maxNodes) {
            val (n, board) = queue.removeFirst()
            val key = board.positionKey
            n.positionMasterGames = index.masterGames(key); n.positionClubGames = index.clubGames(key)
            if (n.moves.size >= rules.maxPlies) continue
            val master = index.masterMoves(key); val total = n.positionMasterGames
            val club = index.clubMoves(key); val clubTotal = n.positionClubGames
            val chosen = linkedMapOf<String, String>()
            if (total >= rules.minMasterPosition) master.entries.sortedByDescending { it.value }
                .filter { it.value >= rules.minMasterMove && it.value.toDouble() / total >= rules.minMasterShare }.forEach { chosen[it.key] = "master" }
            else master.maxByOrNull { it.value }?.takeIf { it.value >= rules.mainLineGames && it.value * 2 > total / 2 }?.let { chosen[it.key] = "master" }
            // Follow every named line that passes through this position (position keys, so transpositions count).
            if (key in names.onNamedRoute) for (move in board.legalMoves()) {
                if (move.uci in chosen) continue
                if ((key to board.apply(move).positionKey) in routeEdges) chosen[move.uci] = "named"
            }
            if (clubTotal >= rules.clubMoveGames) club.entries.filter { it.value >= rules.clubMoveGames / 2 && it.value.toDouble() / clubTotal >= rules.clubMoveShare && it.key !in chosen }
                .forEach { chosen[it.key] = "club" }
            for ((uci, source) in chosen) {
                val t = board.sanAndPlay(ChessMove.fromUci(uci))
                val child = node(n.id, n.moves + uci, t.san, key, t.position, source)
                nodes += child
                val seen = expandedAt[t.position.positionKey]
                if (seen != null) { child.transposesTo = seen; continue }
                expandedAt[t.position.positionKey] = child.id
                // Club-only moves are shown but not expanded by data (the engine handles them later).
                if (source != "club") queue += child to t.position
            }
        }
        return nodes
    }

    private var routeEdges: Set<Pair<String, String>> = names.routeEdges
    fun withRouteEdges(edges: Set<Pair<String, String>>) = apply { routeEdges = edges }

    private fun node(parent: String?, moves: List<String>, san: String, beforeKey: String, after: BoardPosition, source: String): MapNode {
        val uci = moves.last()
        val bands = Band.entries.associate { b -> b.label to (index.stat(b, beforeKey, uci)?.games ?: 0) }
        val name = names.byPosition[after.positionKey]
        return MapNode(TreeBuilder.idFor(moves), parent, moves, san, name, name != null,
            Band.entries.filter { it != Band.CLUB }.sumOf { index.stat(it, beforeKey, uci)?.games ?: 0 }, index.stat(Band.CLUB, beforeKey, uci)?.games ?: 0,
            bands, source)
    }

    companion object {
        /** Consecutive position pairs along every named route (for following named lines through transpositions). */
        fun routeEdges(family: String): Set<Pair<String, String>> = TaxonomyNames.load(family).routeEdges
    }
}
