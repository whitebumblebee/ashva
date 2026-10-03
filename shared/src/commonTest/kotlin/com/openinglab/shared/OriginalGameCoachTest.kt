// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared

import com.openinglab.shared.analysis.AnalysisPosition
import com.openinglab.shared.chess.*
import com.openinglab.shared.data.OfflineFirstOpeningRepository
import com.openinglab.shared.games.*
import com.openinglab.shared.lesson.*
import com.openinglab.shared.model.PieceColor
import kotlin.test.*

class OriginalGameCoachTest {
    private fun graph(pgn: String) = LessonGraph.fromPgn(PrivateGameRecord.import(pgn).checkedGame())

    @Test fun eachOriginalMoveHasCheckedBoardFactsIndependentOfPov() {
        val graph = graph("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 *")
        val original = graph.paths.getValue(graph.originalPathId)
        for (ply in 1..original.moves.size) {
            val facts = OriginalGameCoach.explain(graph, ply)
            assertEquals(original.moves[ply - 1].san, facts.idea.san)
            assertEquals(original.moves[ply - 1].move.uci, facts.idea.uci)
            assertEquals(original.positions[ply - 1].toFen(), facts.beforeFen)
            assertEquals(original.positions[ply].toFen(), facts.afterFen)
            assertEquals(original.positions[ply - 1].sideToMove, facts.movingSide)
            assertEquals(OriginalGameCoach.VERSION, facts.version)
            for (side in PieceColor.entries) {
                val replay = graph.start(side).jump(ply)
                val analysis = OriginalGameCoach.analysisPosition(replay)
                assertEquals(replay.position, analysis.board())
                assertEquals(original.moves.take(ply).map { it.move.uci }, analysis.moves)
            }
        }
    }

    @Test fun userCommentNagsAndRavNeverBecomeHistoricalIntentOrReplaceOriginal() {
        val graph = graph("1. e4 {Invented intent and guaranteed win} $1 (1. d4 d5) e5 *")
        val facts = OriginalGameCoach.explain(graph, 1)
        assertFalse(facts.idea.explanation.contains("Invented"))
        assertFalse(facts.idea.explanation.contains("guaranteed"))
        assertEquals("e2e4", facts.idea.uci)
        assertEquals(listOf("e4", "e5"), graph.start(PieceColor.BLACK).moves.map { it.san })
        assertTrue(graph.paths.size > 1)
        assertEquals("Invented intent and guaranteed win", graph.start(PieceColor.WHITE).nextMove!!.annotation.comments.single())
    }

    @Test fun declaredBlackFenNumberAndSpecialMovesAreNotAssumedToStartOnWhite() {
        val black = graph("[SetUp \"1\"]\n[FEN \"r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 42\"]\n\n42... O-O *")
        val castle = OriginalGameCoach.explain(black, 1)
        assertEquals(PieceColor.BLACK, castle.movingSide)
        assertTrue(castle.idea.explanation.contains("rook moves to f8"))
        val root = OriginalGameCoach.analysisPosition(black.start(PieceColor.WHITE).jump(1))
        assertFalse(root.hasHistoryFromStart)
        assertEquals(black.initialPosition.toFen(), root.initialFen)
        assertEquals(43, root.board().fullmoveNumber)
        val capture = OriginalGameCoach.explain(graph("1. e4 d5 2. exd5 *"), 3)
        assertTrue(capture.idea.explanation.contains("captures the pawn"))
        val promotion = OriginalGameCoach.explain(graph("[SetUp \"1\"]\n[FEN \"4k3/P7/8/8/8/8/8/4K3 w - - 0 1\"]\n\n1. a8=N *"), 1)
        assertTrue(promotion.idea.explanation.contains("promotes on a8 to a knight"))
    }

    @Test fun originalOnlyBoundsAndCancellationAreExplicit() {
        val graph = graph("1. e4 e5 *")
        assertFailsWith<IllegalArgumentException> { OriginalGameCoach.explain(graph, 0) }
        assertFailsWith<IllegalArgumentException> { OriginalGameCoach.explain(graph, 3) }
        var checkpoints = 0
        assertFailsWith<IllegalStateException> { OriginalGameCoach.explain(graph, 1) { checkpoints++; error("fixture cancellation") } }
        assertEquals(1, checkpoints)
        val opening = LessonGraph.fromOpening(OfflineFirstOpeningRepository().getOpenings().first())
        assertFailsWith<IllegalArgumentException> { OriginalGameCoach.explain(opening, 1) }
        assertFailsWith<IllegalArgumentException> { OriginalGameCoach.analysisPosition(opening.start(PieceColor.WHITE)) }
    }

    @Test fun longHistoryRemainsReplayableButNeverDropsEarlierRepetitionsForAnalysis() {
        val text = (1..129).joinToString(" ") { "${it * 2 - 1}. Nf3 Nf6 ${it * 2}. Ng1 Ng8" } + " *"
        val graph = graph(text)
        val atBound = graph.start(PieceColor.BLACK).jump(AnalysisPosition.MAX_HISTORY_PLIES)
        assertEquals(512, OriginalGameCoach.analysisPosition(atBound).moves.size)
        val beyond = atBound.next()
        assertEquals(513, beyond.ply)
        assertEquals("Nf3", OriginalGameCoach.explain(graph, beyond.ply).idea.san)
        assertFailsWith<IllegalArgumentException> { OriginalGameCoach.analysisPosition(beyond) }
        assertEquals(516, beyond.last().ply)
    }
}
