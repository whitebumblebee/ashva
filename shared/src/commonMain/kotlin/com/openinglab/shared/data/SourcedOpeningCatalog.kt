// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.data

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.OpeningMatch
import com.openinglab.shared.chess.OpeningMatchKind
import com.openinglab.shared.content.*
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.model.*

/** Presentation of a validated complete taxonomy snapshot, not an authored repertoire course. */
class SourcedOpeningCatalog(val manifest: ContentManifest, val records: List<OpeningRecord>) {
    val openings: List<Opening>
    private val byRecord: Map<String, Opening>
    private val searchLabels: Map<String, String>
    private val index = SourcedOpeningIndex(records)

    init {
        require(manifest.source.kind == SourceKind.OPENING_TAXONOMY)
        require(records.isNotEmpty() && records.size == manifest.coverage.acceptedRecords)
        require(records.map { it.id }.distinct().size == records.size)
        openings = records.groupBy { it.family }.entries.sortedBy { it.key }.map { (family, routes) ->
            require(routes.size <= LessonGraph.MAX_PATHS && routes.sumOf { it.uci.size } <= LessonGraph.MAX_TOTAL_PLIES) {
                "Source family exceeds replay limits; no routes were silently omitted."
            }
            val ordered = routes.sortedWith(compareBy<OpeningRecord> { it.uci.size }.thenBy { it.eco }.thenBy { it.name }.thenBy { it.id })
            val min = routes.minOf { it.uci.size }; val max = routes.maxOf { it.uci.size }
            val source = manifest.source
            Opening(id = "source:${manifest.packId}:$family", name = family, family = "Sourced taxonomy",
                eco = routes.map { it.eco }.distinct().sorted().let { if (it.size == 1) it.single() else "${it.first()}–${it.last()}" },
                side = OpeningSide.BOTH, difficulty = Difficulty.FOUNDATION,
                description = "${routes.size} source routes · $min–$max half-moves. Name-derived grouping; not full theory or reviewed teaching.",
                identity = "${source.title} · ${source.license}", accentHex = 0xFFB6C9A8,
                progress = 0, keyIdeas = listOf("Reviewed strategic plans are not supplied by this taxonomy.",
                    "Coverage ends at each source route's final move; uncovered moves are not proven bad."),
                variations = ordered.map { record ->
                    require(record.uci.isNotEmpty() && record.uci.size == record.san.size)
                    Variation(record.id, record.name, "${record.eco} · sourced route",
                        "${record.uci.size} half-moves · ${source.title}. Strategic annotations unavailable.",
                        record.uci.mapIndexed { i, uci -> MoveStep(uci, record.san[i], "Source move",
                            "${if (i % 2 == 0) "White" else "Black"} plays ${record.san[i]} in the recorded taxonomy route. A reviewed explanation of this move is not available.",
                            "Source sequence, not an engine recommendation or a winning claim.") },
                        whiteIdea = "A reviewed White plan is not available for this sourced route.",
                        blackIdea = "A reviewed Black plan is not available for this sourced route.")
                }, recognitionPly = ordered.first().uci.size,
                provenance = OpeningProvenance(manifest.packId, source.revision, source.title, source.url,
                    source.license, source.attribution, min, max))
        }
        byRecord = openings.flatMap { opening -> opening.variations.map { it.id to opening } }.toMap()
        searchLabels = openings.associate { opening -> opening.id to
            searchText(opening.name + " " + opening.variations.joinToString(" ") { "${it.name} ${it.category}" }) }
    }

    fun getOpening(id: String): Opening? = openings.firstOrNull { it.id == id }
    fun search(query: String): List<Opening> {
        val terms = searchText(query.trim()).split(Regex("\\s+")).filter { it.isNotBlank() }
        return openings.filter { opening ->
            terms.all { it in searchLabels.getValue(opening.id) }
        }
    }

    // Common opening-name spelling aliases; source labels and immutable records stay unchanged.
    private fun searchText(text: String) = text.lowercase().replace("defence", "defense")
        .replace('ó', 'o').replace('ü', 'u').replace('é', 'e').replace('á', 'a')
        .replace('’', '\'').replace('–', '-')

    /** Endpoint names only. An earlier named endpoint is explicitly last-known, not current. */
    fun identify(positions: List<BoardPosition>): OpeningMatch? {
        val match = index.identify(positions) ?: return null
        val candidates = match.openings.map { byRecord.getValue(it.id) }.distinctBy { it.id }
        val names = match.openings.map { it.name }.distinct()
        return OpeningMatch(candidates.singleOrNull(), names.singleOrNull(), candidates,
            if (!match.atCurrentPosition) OpeningMatchKind.OUT_OF_BOOK
            else if (candidates.size == 1) OpeningMatchKind.KNOWN else OpeningMatchKind.AMBIGUOUS,
            names, match.matchedAtPly, isNamedPosition = true)
    }
}
