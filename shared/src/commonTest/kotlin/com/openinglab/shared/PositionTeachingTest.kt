// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared

import com.openinglab.shared.chess.*
import com.openinglab.shared.lesson.*
import com.openinglab.shared.model.*
import kotlin.test.*

class PositionTeachingTest {
    private fun play(vararg san: String): BoardPosition = san.fold(BoardPosition.starting()) { board, text -> board.parseSanAndPlay(text).position }

    @Test fun frenchPawnBreaksAreLegalCurrentTurnContactsNotRecommendations() {
        val board = play("e4", "e6", "d4", "d5", "e5")
        val black = PositionTeacher.inspect(board, PieceColor.BLACK)
        assertTrue(black.pawnBreaks.any { it.uci == "c7c5" && "d4" in it.contactSquares })
        assertTrue(black.pawnBreaks.any { it.uci == "f7f6" && "e5" in it.contactSquares })
        assertTrue(black.own.points.any { it.topic == "Locked centre" && it.text.contains("d5") })
        black.pawnBreaks.forEach { candidate ->
            val transition = board.sanAndPlay(ChessMove.fromUci(candidate.uci))
            assertEquals(candidate.san, transition.san)
            assertEquals(PieceColor.BLACK, board.pieceAt(candidate.uci.take(2))!!.color)
        }
        val white = PositionTeacher.inspect(board, PieceColor.WHITE)
        assertEquals(black.own, white.opponent)
        assertEquals(black.opponent, white.own)
        assertEquals(black.pawnBreaks, white.pawnBreaks)
        assertEquals(board.toFen(), white.fen)
    }

    @Test fun structureFilesAndEndgameGuidanceAreFactBound() {
        val board = BoardPosition.fromFen("4k3/7p/2P5/2p1p3/3P4/3P4/8/4K3 w - - 0 1")
        val report = PositionTeacher.inspect(board, PieceColor.WHITE)
        assertTrue(report.focus.contains("Reduced-material lens"))
        val structure = report.own.points.single { it.topic == "Pawn structure" }.text
        assertTrue(structure.contains("Doubled files: d"))
        assertTrue(structure.contains("Passed pawns: c6"))
        assertTrue(report.own.points.single { it.topic == "Files" }.text.contains("Half-open files for White: e, h"))
        assertTrue(report.own.points.any { it.topic == "Passed-pawn plan" && it.evidence == TeachingEvidence.CONDITIONAL_PLAN })
        assertTrue(report.own.points.any { it.topic == "Endgame priorities" && it.text.contains("not opposition, zugzwang or tablebase proof") })
        val iqp = PositionTeacher.inspect(BoardPosition.fromFen("4k3/2p2p2/3p4/8/3P4/8/8/4K3 w - - 0 1"), PieceColor.WHITE)
        assertTrue(iqp.own.points.single { it.topic == "Pawn structure" }.text.contains("Isolated pawns: d4"))
        assertTrue(iqp.own.points.single { it.topic == "Structure trade-off" }.text.contains("Neither structure is automatically weak"))
    }

    @Test fun pinnedAttackersRemainGeometricAndPinsDoNotPromiseMaterial() {
        val board = BoardPosition.fromFen("4k3/4n3/8/8/8/8/8/4R1K1 b - - 0 1")
        assertEquals(listOf("e7"), board.attackersOf("f5", PieceColor.BLACK))
        assertTrue(board.isSquareAttacked("f5", PieceColor.BLACK))
        assertFalse(board.isLegal(ChessMove.fromUci("e7f5")))
        val pin = PositionTeacher.inspect(board, PieceColor.WHITE).geometricMotifs.single { it.topic == "King-ray pin" }
        assertTrue(pin.text.contains("e1, enemy piece e7 and enemy king e8"))
        assertTrue(pin.text.contains("not a claim that it can be won"))
        val blockedRay = BoardPosition.fromFen("4k3/4q3/4n3/8/8/8/8/4R1K1 b - - 0 1")
        assertTrue(PositionTeacher.inspect(blockedRay, PieceColor.BLACK).geometricMotifs.none { it.topic == "King-ray pin" })
    }

    @Test fun multipleAttacksDescribeBothColorsWithoutInventingTurnOrCapture() {
        val board = BoardPosition.fromFen("r3k3/2N5/8/8/8/8/8/6K1 b - - 0 1")
        val report = PositionTeacher.inspect(board, PieceColor.BLACK)
        assertEquals(PositionStatus.CHECK, report.status)
        val fork = report.geometricMotifs.single { it.topic == "Multiple attack" }
        assertTrue(fork.text.contains("White knight on c7")); assertTrue(fork.text.contains("a8, e8"))
        assertTrue(fork.text.contains("not proof of winning material"))
        val reflected = board.copy(pieces = board.pieces.map { (s, p) ->
            "${('h'.code - (s[0] - 'a')).toChar()}${('8'.code - (s[1] - '1')).toChar()}" to p.copy(color = p.color.opposite)
        }.toMap(), sideToMove = PieceColor.WHITE)
        assertTrue(PositionTeacher.inspect(reflected, PieceColor.WHITE).geometricMotifs.single { it.topic == "Multiple attack" }.text.contains("Black knight on f2"))
    }

    @Test fun terminalPositionsHaveNoMoveCandidatesAndCheckpointCancellationPropagates() {
        val mate = play("f3", "e5", "g4", "Qh4#")
        val report = PositionTeacher.inspect(mate, PieceColor.WHITE)
        assertEquals(PositionStatus.CHECKMATE, report.status)
        assertTrue(report.pawnBreaks.isEmpty()); assertTrue(report.focus.contains("terminal"))
        val dead = PositionTeacher.inspect(BoardPosition.fromFen("4k3/8/8/8/8/8/8/4K3 w - - 0 1"), PieceColor.WHITE)
        assertEquals(PositionStatus.INSUFFICIENT_MATERIAL, dead.status); assertTrue(dead.pawnBreaks.isEmpty())
        assertFailsWith<UnsupportedOperationException> { PositionTeacher.inspect(BoardPosition.starting(), PieceColor.WHITE) { throw UnsupportedOperationException("cancel fixture") } }
    }

    @Test fun groundedEngineLinesRejectWrongNotationIllegalMovesAndRetainHistory() {
        val uci = listOf("g1f3", "g8f6", "f3g1", "f6g8")
        val san = listOf("Nf3", "Nf6", "Ng1", "Ng8")
        val line = GroundedContinuation.build(BoardPosition.starting(), uci, san)
        assertEquals(5, line.positions.size); assertEquals(4, line.explanations.size)
        assertEquals(2, line.positions.last().repetitionCount)
        assertEquals(uci, line.explanations.map { it.uci })
        assertFailsWith<IllegalArgumentException> { GroundedContinuation.build(BoardPosition.starting(), uci, san.dropLast(1)) }
        assertFailsWith<IllegalArgumentException> { GroundedContinuation.build(BoardPosition.starting(), listOf("e2e5"), listOf("e5")) }
        assertFailsWith<IllegalArgumentException> { GroundedContinuation.build(BoardPosition.starting(), listOf("e2e4"), listOf("e4+")) }
        assertFailsWith<IllegalArgumentException> { GroundedContinuation.build(BoardPosition.starting(), emptyList(), emptyList()) }
    }

    @Test fun geometricAttackerQueryMatchesAttackRuleForEverySquareAndColor() {
        val boards = listOf(BoardPosition.starting(), play("e4", "e5", "Nf3", "Nc6", "Bb5"),
            play("d4", "d5", "Bf4", "Nf6", "e3", "c5"), play("e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nxd4"),
            BoardPosition.fromFen("4k3/4n3/8/8/8/8/8/4R1K1 b - - 0 1"))
        boards.forEach { board ->
            for (f in 'a'..'h') for (r in '1'..'8') for (side in PieceColor.entries) {
                assertEquals(board.isSquareAttacked("$f$r", side), board.attackersOf("$f$r", side).isNotEmpty())
            }
            for (side in PieceColor.entries) {
                val report = PositionTeacher.inspect(board, side)
                assertEquals(side, report.own.side); assertEquals(side.opposite, report.opponent.side)
                assertTrue(report.own.points.any { it.evidence == TeachingEvidence.CONDITIONAL_PLAN })
                report.pawnBreaks.forEach { assertTrue(board.isLegal(ChessMove.fromUci(it.uci))) }
            }
        }
    }
}
