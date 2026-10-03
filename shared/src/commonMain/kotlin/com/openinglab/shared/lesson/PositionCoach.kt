// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.lesson

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.model.*
import kotlin.math.abs

/** Rules-derived observations, not an evaluation, a GM's intention, or copied commentary. */
object PositionCoach {
    fun explain(before: BoardPosition, move: ChessMove, san: String): MoveStep {
        return explainAndPlay(before, move, san).first
    }

    fun explainAndPlay(before: BoardPosition, move: ChessMove, san: String): Pair<MoveStep, BoardPosition> {
        val moving = requireNotNull(before.pieceAt(move.from))
        val after = before.apply(move) // Illegal moves fail closed, including king safety.
        val side = moving.color.name.lowercase().replaceFirstChar { it.uppercase() }
        val piece = moving.type.name.lowercase()
        val facts = mutableListOf<String>()
        val castle = moving.type == PieceType.KING && abs(move.from[0] - move.to[0]) == 2
        val captured = before.pieceAt(move.to)
        val ep = moving.type == PieceType.PAWN && captured == null && move.from[0] != move.to[0]
        val title: String
        val principle: String
        when {
            castle -> {
                title = "Castle and activate the rook"
                facts += "$side castles ${if (move.to[0] == 'g') "kingside" else "queenside"}; the rook moves to ${if (move.to[0] == 'g') "f" else "d"}${move.to[1]}."
                principle = "Compare king safety on both wings before opening the centre. Castling is not a guarantee of safety."
            }
            move.promotion != null -> {
                title = "Promote the pawn"
                facts += "$side promotes on ${move.to} to a ${move.promotion.name.lowercase()}."
                principle = "Check stalemate and forcing replies when choosing the promotion piece."
            }
            captured != null || ep -> {
                title = "Capture and reassess the position"
                facts += if (ep) "$side captures the pawn on ${move.to[0]}${move.from[1]} en passant." else
                    "$side's $piece captures the ${captured!!.type.name.lowercase()} on ${move.to}."
                principle = "A capture changes material and the available lines. Check recaptures and intermediate checks; a capture is not automatically a gain."
            }
            moving.type in listOf(PieceType.BISHOP, PieceType.KNIGHT) && move.from in homeMinorSquares(moving.color) -> {
                title = "Develop a minor piece"
                facts += "$side develops the $piece from ${move.from} to ${move.to}."
                principle = "Develop with a useful role, then coordinate the pieces and king safety."
            }
            moving.type == PieceType.PAWN -> {
                title = if (move.to[0] in "cdef") "Change the central pawn structure" else "Change the wing pawn structure"
                facts += "$side advances the ${move.from[0]}-pawn to ${move.to}. Pawn moves cannot be taken back."
                principle = if (move.to[0] in "cdef") "Before a pawn break, compare the captures, piece activity and squares left behind." else
                    "Compare the space gained with the squares and king shelter changed by this pawn move."
            }
            else -> {
                title = "Reposition the $piece"
                facts += "$side moves the $piece from ${move.from} to ${move.to}."
                principle = "Ask what the piece now attacks or defends, and which opponent reply changes that role."
            }
        }
        val gained = listOf("d4", "e4", "d5", "e5").filter {
            !before.isSquareAttacked(it, moving.color) && after.isSquareAttacked(it, moving.color)
        }
        if (gained.isNotEmpty()) facts += "$side's pieces now attack ${gained.joinToString(", ")}, central squares not attacked before this move."
        val newlyAttacked = after.pieces.filter { (square, target) ->
            target.color != moving.color && target.type !in listOf(PieceType.KING, PieceType.PAWN) &&
                after.isSquareAttacked(square, moving.color) && !before.isSquareAttacked(square, moving.color)
        }.entries.take(3)
        if (newlyAttacked.isNotEmpty()) facts += "New pressure on ${newlyAttacked.joinToString(", ") { "the ${it.value.type.name.lowercase()} on ${it.key}" }}. An attack is not proof of a winning capture."
        if (after.isInCheck()) facts += "This gives check; the opponent must answer the check legally."
        if (moving.type == PieceType.ROOK && after.pieces.none { (square, p) -> square[0] == move.to[0] && p.type == PieceType.PAWN })
            facts += "The rook is on an open file: neither side has a pawn on the ${move.to[0]}-file."
        if (moving.type == PieceType.PAWN) {
            val count = after.pieces.count { (square, p) -> square[0] == move.to[0] && p == Piece(moving.color, PieceType.PAWN) }
            if (count > 1) facts += "$side now has $count pawns on the ${move.to[0]}-file; consider their mobility and support rather than assuming they are weak."
        }
        if (!castle && after.isSquareAttacked(move.to, moving.color.opposite))
            facts += "The piece on ${move.to} is attacked by the opponent's pieces. Calculate the exchanges; this alone does not make the move a mistake."
        return MoveStep(move.uci, san, title, facts.joinToString(" "), principle) to after
    }

    private fun homeMinorSquares(side: PieceColor) = if (side == PieceColor.WHITE) setOf("b1", "g1", "c1", "f1") else setOf("b8", "g8", "c8", "f8")

    fun endpoint(board: BoardPosition, side: PieceColor): String {
        val pawns = board.pieces.filterValues { it == Piece(side, PieceType.PAWN) }.keys.sorted()
        val central = pawns.filter { it[0] in "cdef" }
        val doubled = pawns.groupBy { it[0] }.filterValues { it.size > 1 }.keys.sorted()
        val isolated = pawns.filter { pawn -> pawns.none { abs(it[0] - pawn[0]) == 1 } }
        val undeveloped = homeMinorSquares(side).count { board.pieceAt(it)?.let { p -> p.color == side && p.type in listOf(PieceType.BISHOP, PieceType.KNIGHT) } == true }
        return buildString {
            append("At this route's endpoint, your central pawns are ${central.joinToString(", ").ifEmpty { "absent" }}. ")
            if (doubled.isNotEmpty()) append("Multiple pawns share ${doubled.joinToString(", ")} files. ")
            if (isolated.isNotEmpty()) append("Pawns with no friendly pawn on an adjacent file: ${isolated.joinToString(", ")}. ")
            if (undeveloped > 0) append("$undeveloped minor pieces remain on their starting squares. ")
            append("These are board facts, not an evaluation. Compare a supported pawn break, improving the least active piece, and the opponent's immediate threats. The route ending is not the end of the game.")
        }
    }
}
