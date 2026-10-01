package com.openinglab.shared.lesson

import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.data.OpeningCatalog
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PracticeAttemptTest {
    private val ruy = LessonGraph.fromOpening(OpeningCatalog.byId("ruy-lopez"))

    @Test fun expectedAttemptDoesNotMutateReplay() {
        val replay = ruy.start(PieceColor.WHITE)
        val before = replay.position
        assertEquals(AttemptKind.EXPECTED, replay.assess(ChessMove.fromUci("e2e4")).kind)
        assertEquals(0, replay.ply)
        assertEquals(before, replay.position)
    }

    @Test fun legalOffLineIsNotAnIllegalMove() {
        val attempt = ruy.start(PieceColor.WHITE).assess(ChessMove.fromUci("d2d4"))
        assertEquals(AttemptKind.LEGAL_OFF_LINE, attempt.kind)
        assertEquals("e4", attempt.expected.san)
        assertTrue(attempt.branches.isEmpty())
    }

    @Test fun illegalAttemptStillProvidesExpectedMove() {
        val attempt = ruy.start(PieceColor.WHITE).assess(ChessMove.fromUci("e2e5"))
        assertEquals(AttemptKind.ILLEGAL, attempt.kind)
        assertEquals("e2e4", attempt.expected.move.uci)
    }

    @Test fun matchingBranchIsOfferedWithoutSwitching() {
        val replay = ruy.start(PieceColor.BLACK).jump(5)
        val attempt = replay.assess(ChessMove.fromUci("g8f6"))
        assertEquals(AttemptKind.AVAILABLE_BRANCH, attempt.kind)
        assertEquals("ruy-berlin", attempt.branches.single().pathId)
        assertEquals("a6", attempt.expected.san)
        assertEquals("ruy-main", replay.pathId)
        assertEquals(5, replay.ply)
    }

    @Test fun promotionChoiceIsNotSilentlyChangedToExpectedPiece() {
        val graph = LessonGraph.fromPgn(Pgn.parse("[SetUp \"1\"]\n[FEN \"7k/P7/8/8/8/8/8/7K w - - 0 1\"]\n1. a8=N *"))
        val replay = graph.start(PieceColor.WHITE)
        assertEquals(AttemptKind.EXPECTED, replay.assess(ChessMove.fromUci("a7a8n")).kind)
        assertEquals(AttemptKind.LEGAL_OFF_LINE, replay.assess(ChessMove.fromUci("a7a8q")).kind)
        assertEquals(AttemptKind.ILLEGAL, replay.assess(ChessMove.fromUci("a7a8")).kind)
    }

    @Test fun everySeedHasBothColorPlansAndCompletedLessonsRejectAttempts() {
        for (opening in OpeningCatalog.openings) for (path in LessonGraph.fromOpening(opening).paths.values) {
            assertTrue(path.whiteIdea.isNotBlank(), path.id)
            assertTrue(path.blackIdea.isNotBlank(), path.id)
        }
        assertFailsWith<IllegalArgumentException> { ruy.start(PieceColor.WHITE).last().assess(ChessMove.fromUci("a2a3")) }
    }
}
