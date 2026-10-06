// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.data

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.lesson.PositionCoach
import com.openinglab.shared.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

/** New versioned presentation IDs leave immutable source lessons and saved policies unchanged. */
class TeachingCatalog(source: SourcedOpeningCatalog, onDemand: Boolean = false, checkpoint: () -> Unit = {}) {
    private val originals = source.openings.associateBy { "course:v1:${it.id}" }
    private val prepared = MutableStateFlow<Map<String, Opening>>(emptyMap())
    // Only names/counts/guide text for cards and search. Never pass a summary to a lesson graph.
    val summaries: List<Opening> = source.openings.map { original ->
        checkpoint()
        val authored = StudyRoutes.routes.filter { it.family == original.name }
        val lengths = original.variations.map { it.steps.size } + authored.map { it.moves.count { c -> c == ' ' } + 1 }
        presentation(original, original.variations, authored.size, lengths.min(), lengths.max())
    }
    private val searchLabels = summaries.associate { o -> o.id to normalize(o.name + " " +
        o.variations.joinToString(" ") { it.name + " " + it.category } + " " +
        StudyRoutes.routes.filter { it.family == o.name }.joinToString(" ") { it.title + " AUTHORED STUDY CONTINUATION" }) }
    init { if (!onDemand) source.openings.forEach { prepare(it, checkpoint) } }

    private fun presentation(original: Opening, routes: List<Variation>, authoredCount: Int, min: Int, max: Int): Opening {
        val guide = OpeningGuides.forFamily(original.name)
        return original.copy(id = "course:v1:${original.id}", family = "Opening course", progress = 0,
            description = "${original.variations.size} named source routes + $authoredCount authored study continuations. Both-color plans and board-derived move explanations; $min–$max half-moves.",
            identity = "Ashva guidance · source-attributed routes",
            keyIdeas = listOf(guide.white, guide.black, guide.structure, guide.pitfalls), variations = routes,
            teaching = TeachingCoverage("ashva-teaching/1", original.variations.size, authoredCount, guide.specific, min, max))
    }
    private class Prefix(val board: BoardPosition, val step: MoveStep? = null) {
        val children = mutableMapOf<String, Prefix>()
    }
    // Course-local trie shares identical histories; discarded after preparation.
    private fun prepare(original: Opening, checkpoint: () -> Unit): Opening {
        val id = "course:v1:${original.id}"
        prepared.value[id]?.let { return it }
        val root = Prefix(BoardPosition.starting())
        checkpoint()
        val guide = OpeningGuides.forFamily(original.name)
        val sourceRoutes = original.variations.map { route ->
            checkpoint()
            var prefix = root
            val steps = route.steps.map { step ->
                checkpoint()
                val move = ChessMove.fromUci(step.uci)
                prefix = prefix.children.getOrPut(step.uci) {
                    val (explanation, after) = PositionCoach.explainAndPlay(prefix.board, move, step.san)
                    Prefix(after, explanation)
                }
                requireNotNull(prefix.step).also { require(it.san == step.san) }
            }
            val board = prefix.board
            route.copy(steps = steps, description = "${steps.size} half-moves from the named source route. Ashva's board facts and family guidance are separate from source data.",
                whiteIdea = guide.plan(true) + "\n" + OpeningGuides.routeFocus(original.name, route.name) + "\n" + PositionCoach.endpoint(board, PieceColor.WHITE),
                blackIdea = guide.plan(false) + "\n" + OpeningGuides.routeFocus(original.name, route.name) + "\n" + PositionCoach.endpoint(board, PieceColor.BLACK))
        }
        val authored = StudyRoutes.forFamily(original.name)
        val routes = authored + sourceRoutes
        val min = routes.minOf { it.steps.size }; val max = routes.maxOf { it.steps.size }
        val opening = presentation(original, routes, authored.size, min, max)
        checkpoint()
        return prepared.updateAndGet { it + (id to (it[id] ?: opening)) }.getValue(id)
    }
    /** Compatibility for tooling that explicitly asks for all routes. UI uses summaries. */
    val openings: List<Opening> get() = summaries.map { requireNotNull(getOpening(it.id)) }
    fun preparedOpening(id: String): Opening? = prepared.value[id]
    fun contains(id: String): Boolean = id in originals
    /** Worker-only: creates one full course without retaining replay boards. */
    fun getOpening(id: String, checkpoint: () -> Unit = {}): Opening? = originals[id]?.let { prepare(it, checkpoint) }
    fun search(query: String): List<Opening> = searchSummaries(query).map { requireNotNull(getOpening(it.id)) }
    fun searchSummaries(query: String): List<Opening> {
        val terms = normalize(query).split(WHITESPACE).filter { it.isNotEmpty() }
        return summaries.filter { o -> terms.all { it in searchLabels.getValue(o.id) } }
    }
    private companion object { val WHITESPACE = Regex("\\s+") }
    private fun normalize(value: String) = value.lowercase().replace('ó', 'o').replace('ü', 'u').replace('é', 'e').replace("defence", "defense").trim()
}
