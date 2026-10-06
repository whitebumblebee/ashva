// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.tactics

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.storage.contentSha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Puzzle(val id: String, val fen: String, val moves: List<String>, val rating: Int,
    val themes: List<String>, val opening: String? = null)

@Serializable
data class PuzzleSet(val id: String, val name: String, val puzzleIds: List<String>, val category: String)

@Serializable
data class TacticsPack(val schema: Int = 1, val license: String = "CC0-1.0", val puzzles: List<Puzzle>, val sets: List<PuzzleSet>) {
    val byId: Map<String, Puzzle> by lazy { puzzles.associateBy { it.id } }
}

object TacticsPackValidator {
    const val VERSION = 1
    private val id = Regex("[A-Za-z0-9_-]{1,80}")
    private val uci = Regex("[a-h][1-8][a-h][1-8][qrbn]?")
    private val theme = Regex("[A-Za-z0-9]{1,40}")
    private val json = Json { ignoreUnknownKeys = false }
    fun parse(text: String, checkpoint: () -> Unit = {}): TacticsPack {
        require(text.length <= 2 * 1024 * 1024) { "Tactics pack is too large" }
        return decode(text).also { validate(it, checkpoint) }
    }
    fun decode(text: String): TacticsPack {
        require(text.length <= 2 * 1024 * 1024) { "Tactics pack is too large" }
        return json.decodeFromString<TacticsPack>(text)
    }
    fun validate(pack: TacticsPack, checkpoint: () -> Unit = {}) {
        validateStructure(pack, checkpoint)
        pack.puzzles.forEach { checkpoint(); validatePuzzle(it) }
    }
    /** Matching markers belong only to checksum-verified immutable packs. */
    fun validateOnce(pack: TacticsPack, cache: com.openinglab.shared.data.PackValidationCache, checkpoint: () -> Unit = {}) {
        validateStructure(pack, checkpoint)
        if (cache.isValidated("pack")) return
        pack.puzzles.forEach { checkpoint(); validatePuzzle(it) }
        checkpoint()
        cache.markValidated("pack")
    }
    /** Metadata checks for Home; solutions are legally replayed before use or in the worker. */
    fun validateStructure(pack: TacticsPack, checkpoint: () -> Unit = {}) {
        require(pack.schema == 1 && pack.license == "CC0-1.0")
        require(pack.puzzles.size in 1..10_000 && pack.sets.size in 1..100)
        require(pack.puzzles.map { it.id }.distinct().size == pack.puzzles.size)
        pack.puzzles.forEach { checkpoint(); validatePuzzleStructure(it) }
        require(pack.sets.map { it.id }.distinct().size == pack.sets.size)
        val ids = pack.puzzles.map { it.id }.toSet()
        pack.sets.forEach { set ->
            checkpoint()
            require(id.matches(set.id) && set.name.isNotBlank() && set.name.length <= 80)
            require(set.category in setOf("WOODPECKER", "THEME", "OPENING", "CUSTOM"))
            require(set.puzzleIds.size in 1..1000 && set.puzzleIds.distinct().size == set.puzzleIds.size)
            require(set.puzzleIds.all { it in ids }) { "Set references a missing puzzle" }
        }
    }
    fun validateSet(pack: TacticsPack, set: PuzzleSet, checkpoint: () -> Unit = {}) {
        set.puzzleIds.forEach { checkpoint(); validatePuzzle(pack.byId.getValue(it)) }
    }
    private fun validatePuzzleStructure(puzzle: Puzzle) {
        require(id.matches(puzzle.id) && puzzle.rating in 0..4000 && puzzle.fen.length <= 120)
        // Includes the opponent's setup ply; solutions finish on the solver's move.
        require(puzzle.moves.size in 2..12 && puzzle.moves.size % 2 == 0)
        require(puzzle.themes.size in 1..40 && puzzle.themes.distinct().size == puzzle.themes.size)
        require(puzzle.themes.all(theme::matches))
        require(puzzle.opening == null || puzzle.opening.length <= 160)
        require(puzzle.moves.all(uci::matches))
    }
    fun validatePuzzle(puzzle: Puzzle) {
        validatePuzzleStructure(puzzle)
        var board = BoardPosition.fromFen(puzzle.fen)
        puzzle.moves.forEach { token ->
            // apply rejects mate/stalemate (no legal move). Check the other automatic endings
            // here, avoiding a full-board legal move scan before apply checks the moving piece.
            require(!board.hasInsufficientMaterial() && board.repetitionCount < 5 && board.halfmoveClock < 150)
            board = board.apply(ChessMove.fromUci(token))
        }
    }
}

@Serializable
data class CustomSetSpec(val name: String, val ratingMin: Int, val ratingMax: Int,
    val themes: Set<String> = emptySet(), val size: Int, val seed: Long) {
    fun validate() {
        require(name.isNotBlank() && name.length <= 80)
        require(ratingMin in 800..2600 && ratingMax in ratingMin..2600)
        require(size in setOf(25, 50, 100, 200))
        require(themes.size <= 40 && themes.all { it.matches(Regex("[A-Za-z0-9]{1,40}")) })
    }
    fun select(pack: TacticsPack): List<String> {
        validate()
        val eligible = pack.puzzles.filter { p -> p.rating in ratingMin..ratingMax && (themes.isEmpty() || p.themes.any { it in themes }) }
        require(eligible.size >= size) { "Only ${eligible.size} puzzles match; widen the range or choose a smaller set." }
        return eligible.map { it.id to contentSha256("$seed|${it.id}".encodeToByteArray()) }
            .sortedWith(compareBy<Pair<String, String>> { it.second }.thenBy { it.first })
            .take(size).map { it.first }
    }
}
