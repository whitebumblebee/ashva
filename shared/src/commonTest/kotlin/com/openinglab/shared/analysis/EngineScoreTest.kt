// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.analysis

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.model.PieceColor
import kotlin.test.*

class EngineScoreTest {
    @Test fun centipawnsMateAndBoundsFlipForEitherPerspective() {
        for (side in PieceColor.entries) {
            val cp = EngineScore.Centipawns(37, ScoreBound.LOWER)
            assertEquals(cp, cp.forSide(side, side))
            assertEquals(EngineScore.Centipawns(-37, ScoreBound.UPPER), cp.forSide(side, side.opposite))
            assertEquals(EngineScore.Mate(-3), EngineScore.Mate(3).forSide(side, side.opposite))
            assertEquals(EngineScore.Mate(2, ScoreBound.LOWER), EngineScore.Mate(-2, ScoreBound.UPPER).forSide(side, side.opposite))
        }
    }
    @Test fun budgetsAreBoundedWithoutSilentClamping() {
        assertFailsWith<IllegalArgumentException> { AnalysisBudget(multiPv = 6) }
        assertFailsWith<IllegalArgumentException> { AnalysisBudget(nodes = 200_001) }
        assertFailsWith<IllegalArgumentException> { AnalysisBudget(moveTimeMillis = 3_001) }
        assertFailsWith<IllegalArgumentException> { AnalysisBudget(hashMiB = 33) }
        assertFailsWith<IllegalArgumentException> { AnalysisBudget(depth = 0) }
        assertFailsWith<IllegalArgumentException> { AnalysisPosition(BoardPosition.START_FEN, List(513) { "e2e4" }) }
    }
    @Test fun cacheKeysRetainClocksHistoryMoveAndExactEngineNetworkAndBudget() {
        val identity = EngineIdentity("Fixture", "1", "a".repeat(64), mapOf("nn-aaaaaaaaaaaa.nnue" to "a".repeat(64)))
        val start = AnalysisPosition(BoardPosition.START_FEN)
        val repetition = start.copy(moves = listOf("g1f3", "g8f6", "f3g1", "f6g8"))
        assertEquals(start.board().positionKey, repetition.board().positionKey)
        val key = AnalysisCacheKey(start, AnalysisBudget(), identity, "e2e4")
        assertNotEquals(key, key.copy(position = repetition))
        assertNotEquals(key, key.copy(position = start.copy(initialFen = BoardPosition.START_FEN.replace("0 1", "99 1"))))
        assertNotEquals(key, key.copy(originalMove = "d2d4"))
        assertNotEquals(key, key.copy(budget = AnalysisBudget(nodes = 100)))
        assertNotEquals(key, key.copy(engine = identity.copy(version = "2")))
        assertNotEquals(key, key.copy(engine = identity.copy(networks = mapOf("nn-bbbbbbbbbbbb.nnue" to "b".repeat(64)))))
        assertTrue(start.hasHistoryFromStart)
        assertFalse(start.copy(initialFen = repetition.board().toFen()).hasHistoryFromStart)
    }
    @Test fun illegalHistoriesAndCommandInjectionAreRejectedBeforeTransport() {
        assertFailsWith<IllegalArgumentException> { AnalysisPosition(BoardPosition.START_FEN, listOf("e2e5")).board() }
        assertFailsWith<IllegalArgumentException> { AnalysisPosition(BoardPosition.START_FEN, listOf("e2e4\nquit")).board() }
    }
}
