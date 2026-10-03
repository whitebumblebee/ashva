package com.openinglab.shared.chess

import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceType

data class SanTransition(val move: ChessMove, val san: String, val position: BoardPosition)

/** One checked legal transition supplies notation AND the resulting history-aware position. */
fun BoardPosition.sanAndPlay(move: ChessMove): SanTransition {
    val legal = legalMoves()
    require(move in legal) { "Cannot annotate illegal move ${move.uci}" }
    return annotateLegal(move, legal)
}

fun BoardPosition.san(move: ChessMove): String = sanAndPlay(move).san

/** Import tolerates zero-style castling and annotation glyphs, but not ambiguous/illegal moves. */
fun BoardPosition.parseSan(text: String): ChessMove = parseSanAndPlay(text).move

fun BoardPosition.parseSanAndPlay(text: String): SanTransition {
    val token = text.trim().replace('0', 'O').replace(Regex("[!?]+$"), "")
    val base = token.trimEnd('+', '#')
    val legal = legalMoves()
    val target = Regex("[a-h][1-8]").findAll(base).lastOrNull()?.value
    val candidates = legal.filter { move ->
        // Match canonical base notation without replaying every unrelated candidate.
        (base.startsWith("O-O") || target == move.to) && formatSanBase(move, legal) == base
    }
    require(candidates.size == 1) { "Illegal or ambiguous SAN '$text' in ${toFen()}" }
    val transition = annotateLegal(candidates.single(), legal)
    if (token.endsWith('#')) require(transition.san.endsWith('#')) { "Incorrect mate suffix: $text" }
    if (token.endsWith('+')) require(transition.position.isInCheck()) { "Incorrect check suffix: $text" }
    return transition
}

private fun BoardPosition.annotateLegal(move: ChessMove, legal: List<ChessMove>): SanTransition {
    // Both callers constructed `legal` from this exact immutable position and checked membership.
    val next = advanceKnownLegal(move)
    val suffix = if (!next.isInCheck()) "" else if (next.legalMoves().isEmpty()) "#" else "+"
    return SanTransition(move, formatSanBase(move, legal) + suffix, next)
}

private fun BoardPosition.formatSanBase(move: ChessMove, legal: List<ChessMove>): String {
    val piece = pieces.getValue(move.from)
    val capture = pieces[move.to] != null || (piece.type == PieceType.PAWN && move.from[0] != move.to[0])
    return if (piece.type == PieceType.KING && kotlin.math.abs(move.from[0] - move.to[0]) == 2) {
        if (move.to[0] == 'g') "O-O" else "O-O-O"
    } else buildString {
        if (piece.type != PieceType.PAWN) {
            append(piece.fenChar().uppercaseChar())
            val others = legal.filter { it.to == move.to && it.from != move.from && pieces[it.from]?.type == piece.type }
            if (others.isNotEmpty()) when {
                others.none { it.from[0] == move.from[0] } -> append(move.from[0])
                others.none { it.from[1] == move.from[1] } -> append(move.from[1])
                else -> append(move.from)
            }
        } else if (capture) append(move.from[0])
        if (capture) append('x')
        append(move.to)
        move.promotion?.let { append('='); append(piece.copy(type = it).fenChar().uppercaseChar()) }
    }
}
