// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.data

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.lesson.PositionCoach
import com.openinglab.shared.model.*

/** New versioned presentation IDs leave immutable source lessons and saved policies unchanged. */
class TeachingCatalog(source: SourcedOpeningCatalog, checkpoint: () -> Unit = {}) {
    private class Prefix(val board: BoardPosition, val step: MoveStep? = null) {
        val children = mutableMapOf<String, Prefix>()
    }
    // Constructor-local trie shares only identical move histories, never just transposed positions.
    // It is discarded after presentation construction; the course retains no board cache.
    private fun courses(source: SourcedOpeningCatalog, checkpoint: () -> Unit): List<Opening> {
        val root = Prefix(BoardPosition.starting())
        return source.openings.map { original ->
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
        original.copy(id = "course:v1:${original.id}", family = "Opening course", progress = 0,
            description = "${sourceRoutes.size} named source routes + ${authored.size} authored study continuations. Both-color plans and board-derived move explanations; $min–$max half-moves.",
            identity = "Ashva guidance · source-attributed routes",
            keyIdeas = listOf(guide.white, guide.black, guide.structure, guide.pitfalls), variations = routes,
            teaching = TeachingCoverage("ashva-teaching/1", sourceRoutes.size, authored.size, guide.specific, min, max))
    }
    }
    val openings: List<Opening> = courses(source, checkpoint)
    fun getOpening(id: String) = openings.firstOrNull { it.id == id }
    fun search(query: String): List<Opening> {
        val terms = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return openings.filter { o ->
            val text = normalize(o.name + " " + o.variations.joinToString(" ") { it.name + " " + it.category })
            terms.all { it in text }
        }
    }
    private fun normalize(value: String) = value.lowercase().replace('ó', 'o').replace('ü', 'u').replace('é', 'e').replace("defence", "defense").trim()
}
