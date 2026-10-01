package com.openinglab.shared.chess

import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.model.PieceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChessRulesTest {
    private fun play(vararg moves: String, initial: BoardPosition = BoardPosition.starting()) =
        moves.fold(initial) { board, uci -> board.apply(ChessMove.fromUci(uci)) }

    @Test fun rejectsInvalidUciAndIllegalMoves() {
        for (uci in listOf("a0a1", "i2e4", "e2e4x", "e2e2", "0000"))
            assertFailsWith<IllegalArgumentException> { ChessMove.fromUci(uci) }
        for (uci in listOf("e2e5", "e7e5", "a1a4", "e1g1", "e2e4q"))
            assertFailsWith<IllegalArgumentException> { BoardPosition.starting().apply(ChessMove.fromUci(uci)) }
    }

    @Test fun pinnedPieceCannotExposeKing() {
        val board = BoardPosition.fromFen("4r1k1/8/8/8/8/8/4R3/4K3 w - - 0 1")
        assertFalse("d2" in board.legalTargets("e2"))
        assertTrue("e8" in board.legalTargets("e2"))
    }

    @Test fun checkMustBeAnsweredAndKingsCannotTouch() {
        val board = BoardPosition.fromFen("4r1k1/8/8/8/8/8/P7/4K3 w - - 0 1")
        assertTrue(board.isInCheck())
        assertTrue(board.legalTargets("a2").isEmpty())
        val kings = BoardPosition.fromFen("8/8/8/8/8/4k3/8/4K3 w - - 0 1")
        assertFalse("e2" in kings.legalTargets("e1"))
        assertFailsWith<IllegalArgumentException> { BoardPosition.fromFen("8/8/8/8/8/8/4k3/4K3 w - - 0 1") }
    }

    @Test fun castlingCannotCrossCheckOrLandInCheck() {
        val transit = BoardPosition.fromFen("4kr2/8/8/8/8/8/8/4K2R w K - 0 1")
        assertFalse("g1" in transit.legalTargets("e1"))
        val destination = BoardPosition.fromFen("4k1r1/8/8/8/8/8/8/4K2R w K - 0 1")
        assertFalse("g1" in destination.legalTargets("e1"))
        val checked = BoardPosition.fromFen("4r1k1/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertFalse("c1" in checked.legalTargets("e1"))
        assertFalse("g1" in checked.legalTargets("e1"))
    }

    @Test fun rookOrKingReturningHomeDoesNotRestoreRights() {
        val board = BoardPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val rook = play("h1h2", "h8h7", "h2h1", "h7h8", initial = board)
        assertFalse("g1" in rook.legalTargets("e1"))
        assertTrue("c1" in rook.legalTargets("e1"))
        val king = play("e1f1", "e8f8", "f1e1", "f8e8", initial = board)
        assertTrue(king.castlingRights.isEmpty())
    }

    @Test fun rookCaptureRemovesRightsAndQueensideAllowsAttackedBFile() {
        val board = BoardPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val captured = board.apply(ChessMove.fromUci("a1a8"))
        assertFalse(CastlingRight.BLACK_QUEEN in captured.castlingRights)
        assertFalse(CastlingRight.WHITE_QUEEN in captured.castlingRights)
        val attackedB = BoardPosition.fromFen("1r2k3/8/8/8/8/8/8/R3K3 w Q - 0 1")
        assertTrue("c1" in attackedB.legalTargets("e1"))
        assertEquals(PieceType.ROOK, attackedB.apply(ChessMove.fromUci("e1c1")).pieceAt("d1")?.type)
    }

    @Test fun enPassantCapturesOnlyImmediatelyAndResetsClock() {
        val board = play("e2e4", "a7a6", "e4e5", "d7d5")
        assertTrue("d6" in board.legalTargets("e5"))
        val captured = board.apply(ChessMove.fromUci("e5d6"))
        assertNull(captured.pieceAt("d5"))
        assertEquals(PieceType.PAWN, captured.pieceAt("d6")?.type)
        assertEquals(0, captured.halfmoveClock)
        val expired = play("g1f3", "a6a5", initial = board)
        assertFalse("d6" in expired.legalTargets("e5"))
        val black = play("a2a3", "e7e5", "a3a4", "e5e4", "d2d4")
        assertEquals("exd3", black.san(ChessMove.fromUci("e4d3")))
        assertNull(black.apply(ChessMove.fromUci("e4d3")).pieceAt("d4"))
    }

    @Test fun enPassantCannotExposeKingButCanResolveCheck() {
        val pinned = BoardPosition.fromFen("4k3/8/8/r4pPK/8/8/8/8 w - f6 0 1")
        assertFalse("f6" in pinned.legalTargets("g5"))
        assertEquals(pinned.positionKey, pinned.copy(enPassantTarget = null).positionKey)
        val check = BoardPosition.fromFen("4k3/8/8/3pP3/4K3/8/8/8 w - d6 0 1")
        assertTrue(check.isInCheck())
        assertTrue("d6" in check.legalTargets("e5"))
    }

    @Test fun promotionRequiresChoiceAndSupportsAllFourPieces() {
        val board = BoardPosition.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1")
        assertEquals(BoardPosition.PROMOTIONS.toSet(), board.legalMoves("a7").map { it.promotion }.toSet())
        assertFailsWith<IllegalArgumentException> { board.apply(ChessMove("a7", "a8")) }
        for (type in BoardPosition.PROMOTIONS) assertEquals(type, board.apply(ChessMove("a7", "a8", type)).pieceAt("a8")?.type)
        val capture = BoardPosition.fromFen("1r5k/P7/8/8/8/8/8/7K w - - 0 1")
        assertEquals(8, capture.legalMoves("a7").size)
    }

    @Test fun checkmateStalemateAndMaterialAreDistinct() {
        val mate = play("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(PositionStatus.CHECKMATE, mate.status())
        assertEquals(PieceColor.WHITE, mate.sideToMove)
        val stale = BoardPosition.fromFen("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1")
        assertEquals(PositionStatus.STALEMATE, stale.status())
        assertEquals(PositionStatus.INSUFFICIENT_MATERIAL, BoardPosition.fromFen("7k/8/8/8/8/8/8/K7 w - - 0 1").status())
        assertFalse(BoardPosition.fromFen("7k/8/8/8/8/8/NN6/K7 w - - 0 1").hasInsufficientMaterial())
    }

    @Test fun threefoldClaimAndFivefoldAutomaticDrawUseHistory() {
        val cycle = listOf("g1f3", "g8f6", "f3g1", "f6g8")
        var board = BoardPosition.starting()
        repeat(2) { cycle.forEach { board = board.apply(ChessMove.fromUci(it)) } }
        assertEquals(3, board.repetitionCount)
        assertTrue(board.canClaimThreefold())
        assertEquals(PositionStatus.ONGOING, board.status())
        repeat(2) { cycle.forEach { board = board.apply(ChessMove.fromUci(it)) } }
        assertEquals(PositionStatus.FIVEFOLD_REPETITION, board.status())
        assertEquals(1, BoardPosition.fromFen(board.toFen()).repetitionCount, "FEN does not encode earlier repetitions")
    }

    @Test fun fiftyMoveClaimsAndSeventyFiveMoveDrawAreNotTheSame() {
        val board = BoardPosition.fromFen("7k/8/8/8/8/8/8/KR6 w - - 99 50")
        assertFalse(board.canClaimFiftyMoves())
        assertTrue(board.canClaimFiftyMoves(ChessMove.fromUci("b1b2")))
        assertEquals(PositionStatus.ONGOING, board.copy(halfmoveClock = 100).status())
        assertEquals(PositionStatus.SEVENTY_FIVE_MOVES, board.copy(halfmoveClock = 150).status())
    }

    @Test fun fenRoundTripAndNormalizedIdentityRespectRightsAndLegalEp() {
        val board = play("e2e4")
        assertEquals(board.toFen(), BoardPosition.fromFen(board.toFen()).toFen())
        assertEquals(board.positionKey, board.copy(enPassantTarget = null, halfmoveClock = 37, fullmoveNumber = 80).positionKey)
        assertFalse(BoardPosition.starting().positionKey == BoardPosition.starting().copy(castlingRights = emptySet()).positionKey)
        val ep = play("e2e4", "a7a6", "e4e5", "d7d5")
        assertFalse(ep.positionKey == ep.copy(enPassantTarget = null).positionKey)
        for (fen in listOf("8/8/8/8/8/8/8/8 w - - 0 1", BoardPosition.START_FEN.replace("KQkq", "KK"), BoardPosition.START_FEN.replace(" - 0", " e3 0")))
            assertFailsWith<IllegalArgumentException> { BoardPosition.fromFen(fen) }
    }
}
