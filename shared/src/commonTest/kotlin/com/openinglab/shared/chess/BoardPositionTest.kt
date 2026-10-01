package com.openinglab.shared.chess

import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.model.PieceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardPositionTest {
    @Test
    fun appliesOpeningMovesAndAlternatesTurn() {
        val position = listOf("e2e4", "e7e5", "g1f3", "b8c6").fold(BoardPosition.starting()) { board, uci ->
            board.apply(ChessMove.fromUci(uci))
        }

        assertEquals(PieceType.PAWN, position.pieceAt("e4")?.type)
        assertEquals(PieceType.KNIGHT, position.pieceAt("f3")?.type)
        assertEquals(PieceColor.WHITE, position.sideToMove)
        assertNull(position.pieceAt("g1"))
    }

    @Test
    fun castlingMovesTheRookToo() {
        val position = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1e2", "g8f6", "e1g1").fold(BoardPosition.starting()) { board, uci ->
            board.apply(ChessMove.fromUci(uci))
        }

        assertEquals(PieceType.KING, position.pieceAt("g1")?.type)
        assertEquals(PieceType.ROOK, position.pieceAt("f1")?.type)
        assertNull(position.pieceAt("h1"))
    }

    @Test
    fun legalTargetsRespectBlockingPieces() {
        val board = BoardPosition.starting()
        assertEquals(setOf("a3", "a4"), board.legalTargets("a2"))
        assertTrue(board.legalTargets("c1").isEmpty())
        assertEquals(setOf("a3", "c3"), board.legalTargets("b1"))
    }
}

