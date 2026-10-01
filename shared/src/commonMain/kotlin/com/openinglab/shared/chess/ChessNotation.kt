package com.openinglab.shared.chess

import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceType

fun BoardPosition.san(move: ChessMove): String {
    val legal = legalMoves()
    require(move in legal) { "Cannot annotate illegal move ${move.uci}" }
    return formatSan(move, legal)
}

/** Import tolerates zero-style castling and annotation glyphs, but not ambiguous/illegal moves. */
fun BoardPosition.parseSan(text: String): ChessMove {
    val token = text.trim().replace('0', 'O').replace(Regex("[!?]+$"), "")
    val base = token.trimEnd('+', '#')
    val legal = legalMoves()
    val candidates = legal.filter { move ->
        // Destination filtering avoids evaluating every unrelated candidate's resulting position.
        val target = Regex("[a-h][1-8]").findAll(base).lastOrNull()?.value
        (base.startsWith("O-O") || target == move.to) && formatSan(move, legal).trimEnd('+', '#') == base
    }
    require(candidates.size == 1) { "Illegal or ambiguous SAN '$text' in ${toFen()}" }
    val move = candidates.single()
    if (token.endsWith('#')) require(formatSan(move, legal).endsWith('#')) { "Incorrect mate suffix: $text" }
    if (token.endsWith('+')) require(apply(move).isInCheck()) { "Incorrect check suffix: $text" }
    return move
}

private fun BoardPosition.formatSan(move: ChessMove, legal: List<ChessMove>): String {
    val piece = pieces.getValue(move.from)
    val capture = pieces[move.to] != null || (piece.type == PieceType.PAWN && move.from[0] != move.to[0])
    val base = if (piece.type == PieceType.KING && kotlin.math.abs(move.from[0] - move.to[0]) == 2) {
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
    val next = apply(move)
    return base + if (!next.isInCheck()) "" else if (next.legalMoves().isEmpty()) "#" else "+"
}
