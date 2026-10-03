// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.chess

import com.openinglab.shared.model.ChessMove
import kotlin.test.*

class SanTransitionTest {
    @Test fun checkedCombinedTransitionMatchesApplyIncludingSpecialMovesAndHistory() {
        val positions = listOf(BoardPosition.starting(),
            BoardPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 8 4"),
            BoardPosition.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1"),
            BoardPosition.fromFen("7k/8/8/3pP3/8/8/8/K7 w - d6 0 20"),
            BoardPosition.fromFen("4k3/8/8/8/8/8/2N3N1/4K3 w - - 17 9"))
        for (position in positions) for (move in position.legalMoves()) {
            val transition = position.sanAndPlay(move)
            assertEquals(position.apply(move), transition.position)
            assertEquals(transition, position.parseSanAndPlay(transition.san))
            assertEquals(move, position.parseSan(transition.san))
        }
        var repeated = BoardPosition.starting()
        for (uci in listOf("g1f3", "g8f6", "f3g1", "f6g8")) {
            val move = ChessMove.fromUci(uci)
            val expected = repeated.apply(move)
            repeated = repeated.sanAndPlay(move).position
            assertEquals(expected, repeated)
        }
        assertEquals(2, repeated.repetitionCount)
    }

    @Test fun combinedImportStillRejectsIllegalAmbiguousAndFalseCheckOrMateNotation() {
        assertFailsWith<IllegalArgumentException> { BoardPosition.starting().sanAndPlay(ChessMove.fromUci("e2e5")) }
        for (token in listOf("e5", "e4+", "e4#", "Ke4"))
            assertFailsWith<IllegalArgumentException> { BoardPosition.starting().parseSanAndPlay(token) }
        val ambiguous = BoardPosition.fromFen("4k3/8/8/8/8/8/2N3N1/4K3 w - - 0 1")
        assertFailsWith<IllegalArgumentException> { ambiguous.parseSanAndPlay("Ne3") }
        val castle = BoardPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertEquals("e1g1", castle.parseSanAndPlay("0-0!?").move.uci)
    }
}
