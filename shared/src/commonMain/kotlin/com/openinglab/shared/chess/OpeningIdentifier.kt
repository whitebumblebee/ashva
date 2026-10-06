package com.openinglab.shared.chess

import com.openinglab.shared.data.OpeningCatalog
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.Opening

enum class OpeningMatchKind { EMPTY, KNOWN, AMBIGUOUS, UNKNOWN, OUT_OF_BOOK, INVALID }

data class OpeningMatch(
    val opening: Opening?,
    val variationName: String?,
    val candidates: List<Opening>,
    val kind: OpeningMatchKind,
    val variationNames: List<String> = emptyList(),
    val matchedPly: Int? = null,
    val error: String? = null,
    /** True only after a defining opening position, not for unnamed early candidates. */
    val isNamedPosition: Boolean = false,
)

/** A position index, not a move-order heuristic. Coverage is limited to the provided catalog. */
class OpeningIdentifier(private val openings: List<Opening> = OpeningCatalog.openings) {
    private data class Entry(val opening: Opening, val variationName: String?, val definitionPly: Int, val named: Boolean)
    private val index: Map<String, List<Entry>> by lazy { buildMap {
        for (opening in openings) for (variation in opening.variations) {
            var board = BoardPosition.starting()
            for ((i, step) in variation.steps.withIndex()) {
                board = board.apply(ChessMove.fromUci(step.uci))
                val named = variation.identifiesOpening && i + 1 >= opening.recognitionPly
                val variationKnown = named && i + 1 >= variation.recognitionPly
                val entry = Entry(opening, variation.name.takeIf { variationKnown },
                    if (variationKnown) variation.recognitionPly else opening.recognitionPly, named)
                put(board.positionKey, get(board.positionKey).orEmpty() + entry)
            }
        }
    } }

    fun identify(position: BoardPosition): OpeningMatch = match(position, null)
    fun identifyFen(fen: String): OpeningMatch = try { identify(BoardPosition.fromFen(fen)) }
        catch (error: IllegalArgumentException) { invalid(error) }
    fun identifyPgn(pgn: String): OpeningMatch = try {
        val game = Pgn.parse(pgn)
        identify(game.initialPosition, game.line.plies.map { it.move.uci })
    } catch (error: IllegalArgumentException) { invalid(error) }

    fun identify(moves: List<String>): OpeningMatch = identify(BoardPosition.starting(), moves)

    fun identify(initial: BoardPosition, moves: List<String>): OpeningMatch {
        if (moves.isEmpty()) return if (initial.positionKey == BoardPosition.starting().positionKey)
            OpeningMatch(null, null, openings, OpeningMatchKind.EMPTY) else identify(initial)
        var board = initial
        var lastKnown: OpeningMatch? = match(initial, 0).takeIf { it.isNamedPosition }
        for ((i, uci) in moves.withIndex()) {
            board = try { board.apply(ChessMove.fromUci(uci)) } catch (error: IllegalArgumentException) {
                return invalid(error)
            }
            val current = match(board, i + 1)
            if (current.isNamedPosition) lastKnown = current
            if (i == moves.lastIndex) {
                return if (current.kind == OpeningMatchKind.UNKNOWN && lastKnown != null)
                    lastKnown.copy(kind = OpeningMatchKind.OUT_OF_BOOK) else current
            }
        }
        error("Unreachable")
    }

    private fun match(board: BoardPosition, ply: Int?): OpeningMatch {
        val entries = index[board.positionKey].orEmpty()
        val named = entries.filter { it.named }
        val candidates = (named.ifEmpty { entries }).map { it.opening }.distinctBy { it.id }
        if (named.isEmpty()) return OpeningMatch(null, null, candidates,
            if (candidates.size > 1) OpeningMatchKind.AMBIGUOUS else OpeningMatchKind.UNKNOWN, matchedPly = ply)
        val variations = named.filter { it.variationName != null }
            .let { all -> all.filter { it.definitionPly == all.maxOfOrNull { entry -> entry.definitionPly } } }
            .mapNotNull { it.variationName }.distinct()
        return OpeningMatch(
            opening = candidates.singleOrNull(), variationName = variations.singleOrNull(),
            candidates = candidates, kind = if (candidates.size == 1) OpeningMatchKind.KNOWN else OpeningMatchKind.AMBIGUOUS,
            variationNames = variations, matchedPly = ply, isNamedPosition = true,
        )
    }

    private fun invalid(error: IllegalArgumentException) = OpeningMatch(null, null, emptyList(),
        OpeningMatchKind.INVALID, error = error.message)
}
