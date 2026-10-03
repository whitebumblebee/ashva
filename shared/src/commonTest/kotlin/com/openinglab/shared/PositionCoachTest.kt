// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared

import com.openinglab.shared.chess.*
import com.openinglab.shared.lesson.PositionCoach
import com.openinglab.shared.model.ChessMove
import kotlin.test.*

class PositionCoachTest {
    @Test fun rejectsIllegalMovesRatherThanInventingAnExplanation() {
        assertFailsWith<IllegalArgumentException> { PositionCoach.explain(BoardPosition.starting(), ChessMove.fromUci("e2e5"), "e5") }
    }
    @Test fun pawnDevelopmentAndPressureAreBoardDerived() {
        val board = BoardPosition.starting()
        val step = PositionCoach.explain(board, ChessMove.fromUci("e2e4"), "e4")
        assertTrue(step.explanation.contains("e-pawn to e4"))
        assertTrue(step.explanation.contains("d5"))
        assertFalse(step.explanation.contains("wins"))
        val after = board.apply(ChessMove.fromUci("e2e4")).apply(ChessMove.fromUci("e7e5"))
        assertEquals("Develop a minor piece", PositionCoach.explain(after, ChessMove.fromUci("g1f3"), "Nf3").title)
    }
    @Test fun capturesChecksCastlingAndEnPassantAreExplicit() {
        val ep = BoardPosition.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        assertTrue(PositionCoach.explain(ep, ep.parseSan("exd6"), "exd6").explanation.contains("d5 en passant"))
        val castle = BoardPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertTrue(PositionCoach.explain(castle, castle.parseSan("O-O-O"), "O-O-O").explanation.contains("rook moves to d1"))
        val check = BoardPosition.fromFen("4k3/8/8/8/8/8/8/4K2R w - - 0 1")
        assertTrue(PositionCoach.explain(check, check.parseSan("Rh8+"), "Rh8+").explanation.contains("gives check"))
    }
}
