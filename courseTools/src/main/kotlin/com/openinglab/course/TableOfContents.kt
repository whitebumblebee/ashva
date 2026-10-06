// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class TocVariation(val name: String, val path: String, val ply: Int, val masterGames: Int, val clubGames: Int,
                        val bandGames: Map<String, Int>, val deepestMasterPly: Int, val named: Boolean)

@Serializable
data class TocChapter(val id: String, val title: String, val positions: Int, val masterGamesAtRoot: Int, val clubGamesAtRoot: Int,
                      val deepestMasterPly: Int, val variations: List<TocVariation>)

/** Checkpoint report: what the full course would contain, before any engine work or writing. */
object TableOfContents {
    fun build(config: CourseConfig, nodes: List<MapNode>): List<TocChapter> {
        val byId = nodes.associateBy { it.id }
        val kids = nodes.groupBy { it.parent }
        fun sanPath(n: MapNode): List<String> = generateSequence(n) { it.parent?.let(byId::get) }.map { it.san }.toList().reversed()
        fun inherited(n: MapNode): String? = generateSequence(n) { it.parent?.let(byId::get) }.firstNotNullOfOrNull { it.name }
        val chapterOf = nodes.associate { n -> n.id to chapterFor(config, sanPath(n), inherited(n)) }
        // Deepest ply below a node that is still backed by master games.
        val deepest = HashMap<String, Int>()
        fun depthOf(n: MapNode): Int = deepest.getOrPut(n.id) {
            maxOf(if (n.masterGames >= 1) n.moves.size else 0, kids[n.id].orEmpty().maxOfOrNull { depthOf(it) } ?: 0)
        }
        val variationNodes = HashMap<String, MapNode>()
        fun variation(n: MapNode, name: String, named: Boolean): TocVariation {
            val path = sanPath(n).joinToString(" ")
            variationNodes[path] = n
            return TocVariation(name, path, n.moves.size, n.masterGames, n.clubGames, n.bandGames, depthOf(n), named)
        }
        val chapters = config.chapters.map { chapter ->
            val members = nodes.filter { chapterOf[it.id] == chapter.id }
            val root = nodes.firstOrNull { sanPath(it) == chapter.rootSan }
            val variations = mutableListOf<TocVariation>()
            // Named variations: the first node where each name appears.
            members.filter { it.named && it.name != it.parent?.let(byId::get)?.let(::inherited) }.groupBy { it.name!! }
                .forEach { (name, firsts) -> val f = firsts.minBy { it.moves.size }
                    variations += variation(f, TaxonomyNames.short(name), true) }
            // Important unnamed lines: popular master choices that lead to a position without a name of its own.
            members.filter { !it.named && it.source == "master" && it.masterGames >= 150 }
                .filter { n -> val p = n.parent?.let(byId::get); p != null && n.masterGames.toDouble() / maxOf(1, p.positionMasterGames) >= 0.15 && kids[p.id].orEmpty().size > 1 }
                .forEach { n -> variations += variation(n, "${inherited(n)?.let(TaxonomyNames::short) ?: chapter.title} · ${moveLabel(n.moves.size, n.san)}", false) }
            TocChapter(chapter.id, chapter.title, members.size, root?.positionMasterGames ?: 0, root?.positionClubGames ?: 0,
                members.maxOfOrNull { depthOf(it) } ?: 0, variations.sortedWith(compareBy({ it.path.split(' ').size }, { -it.masterGames })))
        }
        return uniqueVariationNames(chapters, variationNodes, byId)
    }

    private class VariationLabel(val variation: TocVariation, val moves: List<MapNode>) {
        var name = variation.name
        private var firstMove = moves.lastIndex

        fun extend(): Boolean {
            if (firstMove <= 0) return false
            firstMove--
            val sequence = moves.drop(firstMove).mapIndexed { i, n ->
                if (i == 0 || n.moves.size % 2 == 1) moveLabel(n.moves.size, n.san) else n.san
            }.joinToString(" ")
            name = "${variation.name.substringBeforeLast(" · ")} · $sequence"
            return true
        }
    }

    private fun uniqueVariationNames(chapters: List<TocChapter>, variationNodes: Map<String, MapNode>, byId: Map<String, MapNode>): List<TocChapter> {
        val labels = chapters.flatMap { it.variations }.map { v ->
            val moves = if (v.named) emptyList() else generateSequence(variationNodes.getValue(v.path)) { it.parent?.let(byId::get) }
                .takeWhile { !it.named }.toList().reversed()
            VariationLabel(v, moves)
        }
        // Resolve across the whole course, rechecking labels introduced by each extra ply.
        while (true) {
            val collisions = labels.groupBy { it.name }.values.filter { it.size > 1 }
            var extended = false
            for (group in collisions) for (label in group) if (label.extend()) extended = true
            if (!extended) break
        }
        // Keep existing unique labels reserved, including names that already end in a numeric suffix.
        val used = labels.mapTo(HashSet()) { it.name }
        for ((name, group) in labels.groupBy { it.name }.filterValues { it.size > 1 }) {
            var suffix = 2
            for (label in group.sortedBy { it.variation.path }.drop(1)) {
                while (!used.add("$name ($suffix)")) suffix++
                label.name = "$name ($suffix)"
                suffix++
            }
        }
        val resolved = labels.iterator()
        return chapters.map { c -> c.copy(variations = c.variations.map {
            val label = resolved.next()
            label.variation.copy(name = label.name)
        }) }
    }

    /** Variation names decide the chapter first (move orders transpose); otherwise the longest root prefix. */
    fun chapterFor(config: CourseConfig, path: List<String>, name: String?): String? {
        val byName = name?.let { n -> config.chapterNames.entries.firstOrNull { n.contains(it.key) }?.value }
        if (byName != null) return byName
        return config.chapters.filter { c -> path.size >= c.rootSan.size && path.subList(0, c.rootSan.size) == c.rootSan }
            .maxByOrNull { it.rootSan.size }?.id
    }

    fun moveLabel(ply: Int, san: String) = "${(ply + 1) / 2}${if (ply % 2 == 1) "." else "..."}$san"

    fun markdown(config: CourseConfig, chapters: List<TocChapter>, totalPositions: Int, masterGames: Long, clubGames: Long): String = buildString {
        appendLine("# ${config.title} — table of contents (checkpoint)")
        appendLine()
        appendLine("Data-only map, before engine analysis and writing. $totalPositions positions from $masterGames master games (both players 2200+, Lichess broadcasts) and $clubGames club games (Lichess 1600–2200, 5+0 or slower), plus every named line in the CC0 Lichess opening list.")
        appendLine("\"Deepest\" = how far (in moves) master games still continue in that variation; beyond that the engine continues to a verdict.")
        appendLine()
        for (c in chapters) {
            appendLine("## ${c.title}")
            appendLine("${c.positions} positions · ${c.masterGamesAtRoot} master / ${c.clubGamesAtRoot} club games reach the chapter start · master games reach move ${(c.deepestMasterPly + 1) / 2}")
            appendLine()
            appendLine("| Variation | Moves | Master games (2600+ / 2400–2599 / 2200–2399) | Club games | Deepest (move) |")
            appendLine("| --- | --- | --- | --- | --- |")
            for (v in c.variations) {
                val b = v.bandGames
                appendLine("| ${v.name} | ${numbered(v.path)} | ${v.masterGames} (${b["2600+"] ?: 0} / ${b["2400–2599"] ?: 0} / ${b["2200–2399"] ?: 0}) | ${v.clubGames} | ${(v.deepestMasterPly + 1) / 2} |")
            }
            appendLine()
        }
    }

    private fun numbered(path: String) = path.split(' ').mapIndexed { i, s -> if (i % 2 == 0) "${i / 2 + 1}.$s" else s }.joinToString(" ")
}

fun runToc(pipeline: CoursePipeline) {
    val config = pipeline.config
    val index = BandedIndex.build(GameFilter.RUY_PREFIX, 120)
    val names = TaxonomyNames.load("Ruy Lopez")
    val nodes = VariationMapBuilder(index, names, MapRules()).withRouteEdges(VariationMapBuilder.routeEdges("Ruy Lopez")).build(GameFilter.RUY_PREFIX)
    File(pipeline.dir, "map").mkdirs()
    File(pipeline.dir, "map/variation-map.json").writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(MapNode.serializer()), nodes))
    val chapters = TableOfContents.build(config, nodes)
    val masters = listOf(Band.ELITE, Band.GM_IM, Band.MASTER).sumOf { index.bands.getValue(it).games }
    File(pipeline.dir, "map/toc.json").writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TocChapter.serializer()), chapters))
    File(pipeline.dir, "TOC.md").writeText(TableOfContents.markdown(config, chapters, nodes.size, masters, index.bands.getValue(Band.CLUB).games))
    println("Map: ${nodes.size} positions; ${chapters.sumOf { it.variations.size }} variations listed; master games $masters")
    chapters.forEach { println("  ${it.id}: ${it.positions} positions, ${it.variations.size} variations, master depth move ${(it.deepestMasterPly + 1) / 2}") }
}
