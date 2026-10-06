// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.tactics

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.model.PieceType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class TacticsTest {
    private fun puzzle(id: String = "fixture", moves: List<String> = listOf("e2e4", "e7e5", "g1f3", "b8c6")) =
        Puzzle(id, BoardPosition.START_FEN, moves, 1200, listOf("fork"))
    private fun pack(puzzles: List<Puzzle> = listOf(puzzle())) = TacticsPack(puzzles = puzzles,
        sets = listOf(PuzzleSet("easy", "Easy", puzzles.map { it.id }, "WOODPECKER")))

    @Test fun sessionAppliesSetupAndReplyButWrongMovePreservesPosition() {
        val session = PuzzleSession(puzzle())
        assertEquals("e2e4", session.setupMove.uci); assertEquals(PieceColor.BLACK, session.solverSide)
        assertEquals("e4", session.position.lastMove!!.to)
        val before = session.position
        assertEquals("e7e5", assertIs<PuzzleResult.Wrong>(session.submit(ChessMove.fromUci("a7a6"))).expected.uci)
        assertEquals(before, session.position)
        val result = assertIs<PuzzleResult.Correct>(session.submit(ChessMove.fromUci("e7e5")))
        assertEquals("g1f3", result.reply.uci); assertEquals(PieceColor.WHITE, result.afterSolver.sideToMove)
        assertEquals(PieceColor.BLACK, result.position.sideToMove)
        assertIs<PuzzleResult.Solved>(session.submit(ChessMove.fromUci("b8c6")))
        assertTrue(session.solved); assertFails { session.submit(ChessMove.fromUci("b8c6")) }
    }
    @Test fun alternativeCheckmateAcceptedOnFinalMove() {
        val p = Puzzle("mate", "7k/8/6K1/8/8/8/8/RR6 b - - 0 1", listOf("h8g8", "a1a8"), 1200, listOf("mateIn1"))
        TacticsPackValidator.validatePuzzle(p)
        assertIs<PuzzleResult.Solved>(PuzzleSession(p).submit(ChessMove.fromUci("b1b8")))
    }
    @Test fun nonFinalAlternativeAndIllegalMoveAreWrong() {
        val session = PuzzleSession(puzzle())
        assertIs<PuzzleResult.Wrong>(session.submit(ChessMove.fromUci("e7e4")))
        assertIs<PuzzleResult.Wrong>(session.submit(ChessMove.fromUci("d7d5")))
        assertEquals(1, session.nextIndex)
    }
    @Test fun explicitPromotionSuffixIsRequiredAndUnderpromotionWorks() {
        val p = Puzzle("promo", "7k/P7/8/8/8/8/8/7K b - - 0 1", listOf("h8g8", "a7a8n"), 1500, listOf("promotion"))
        TacticsPackValidator.validatePuzzle(p)
        val session = PuzzleSession(p)
        assertIs<PuzzleResult.Wrong>(session.submit(ChessMove.fromUci("a7a8")))
        val solved = assertIs<PuzzleResult.Solved>(session.submit(ChessMove.fromUci("a7a8n")))
        assertEquals(PieceType.KNIGHT, solved.position.pieceAt("a8")!!.type)
    }
    @Test fun packFailsClosedOnDuplicatesMissingReferencesAndIllegalReplay() {
        val p = pack(); TacticsPackValidator.validate(p)
        assertEquals(p, TacticsPackValidator.parse(Json.encodeToString(p)))
        assertFails { TacticsPackValidator.validate(p.copy(puzzles = p.puzzles + p.puzzles)) }
        assertFails { TacticsPackValidator.validate(p.copy(sets = p.sets + p.sets)) }
        assertFails { TacticsPackValidator.validate(p.copy(sets = listOf(p.sets.single().copy(puzzleIds = listOf("absent"))))) }
        assertFails { TacticsPackValidator.validate(pack(listOf(puzzle(moves = listOf("e2e5", "e7e5"))))) }
        assertFails { TacticsPackValidator.validate(pack(listOf(puzzle(moves = listOf("e2e4", "e7e5", "g1f3"))))) }
        assertFails { TacticsPackValidator.parse(Json.encodeToString(p).dropLast(1) + ",\"unknown\":true}") }
    }
    @Test fun cycleOneHasNoTargetThenHalvesWithFiveSecondsPerPuzzleFloor() {
        assertNull(Woodpecker.targetMs(100, null))
        val previous = TacticsCycle("easy", 1, 0, 10, 1_800_000)
        assertEquals(900_000L, Woodpecker.targetMs(100, previous))
        assertEquals(500_000L, Woodpecker.targetMs(100, previous.copy(activeMs = 100_000)))
        assertFails { Woodpecker.targetMs(100, previous.copy(completedAt = null)) }
        assertEquals(7, Woodpecker.RECOMMENDED_CYCLES)
    }
    @Test fun firstAttemptsDefineAccuracyCompletenessAndImprovement() {
        val first = TacticsCycle("easy", 1, 0, 10, 60_000)
        val cycle = TacticsCycle("easy", 2, 10, 20, 30_000)
        val attempts = listOf(TacticsAttempt("easy", 2, "a", 0, true, 10_000, 11), TacticsAttempt("easy", 2, "b", 1, false, 20_000, 20))
        assertFalse(Woodpecker.complete(listOf("a", "b"), attempts.take(1)))
        assertTrue(Woodpecker.complete(listOf("a", "b"), attempts))
        assertEquals(1, Woodpecker.nextOrdinal(listOf("a", "b"), attempts.take(1)))
        val stats = Woodpecker.stats(cycle, attempts, 2, first)
        assertEquals(.5, stats.accuracy); assertEquals(15.0, stats.averageSeconds)
        assertEquals(.5, stats.improvement); assertEquals(30_000L, stats.targetMs)
        assertFails { Woodpecker.complete(listOf("a", "b"), listOf(attempts.last())) }
        assertFails { Woodpecker.complete(listOf("a", "b"), attempts + attempts.first()) }
    }
    @Test fun customSetsAreSeededAnyOfAndInputOrderIndependent() {
        val puzzles = (0..99).map { puzzle("p$it").copy(rating = 1000 + it * 10, themes = listOf(if (it % 2 == 0) "fork" else "pin")) }
        val p = pack(puzzles)
        val spec = CustomSetSpec("Mine", 1000, 1990, setOf("fork", "pin"), 25, 1)
        val ids = spec.select(p)
        // Preserve the old comparator's exact order while computing each hash only once.
        val legacy = puzzles.sortedWith(compareBy<Puzzle> {
            com.openinglab.shared.storage.contentSha256("${spec.seed}|${it.id}".encodeToByteArray())
        }.thenBy { it.id }).take(spec.size).map { it.id }
        assertEquals(legacy, ids)
        assertEquals(25, ids.distinct().size)
        assertEquals(ids, spec.select(p.copy(puzzles = puzzles.reversed())))
        assertNotEquals(ids, spec.copy(seed = 2).select(p))
        assertTrue(spec.copy(themes = setOf("fork")).select(p).all { "fork" in p.byId.getValue(it).themes })
        assertEquals(ids, spec.copy(themes = emptySet()).select(p))
        assertFails { spec.copy(ratingMax = 1010).select(p) }
        assertFails { spec.copy(size = 10).select(p) }
    }

    @Test fun historyIndexesKeepCycleOrderingAndLatestSetSelection() {
        val history = TacticsHistory(cycles = listOf(TacticsCycle("a", 2, 20), TacticsCycle("b", 1, 30), TacticsCycle("a", 1, 10)),
            attempts = listOf(TacticsAttempt("a", 1, "p1", 1, true, 1, 100), TacticsAttempt("a", 1, "p0", 0, false, 1, 99)))
        assertEquals(listOf(1, 2), history.cyclesBySet.getValue("a").map { it.cycle })
        assertEquals(listOf(0, 1), history.attemptsByCycle.getValue("a" to 1).map { it.ordinal })
        assertEquals("a", history.latestCycle?.setId)
        assertEquals(2, history.latestCycle?.cycle)
        assertEquals("b", history.copy(attempts = emptyList()).latestCycle?.setId)
    }
    @Test fun validationStillRejectsMovesAfterAutomaticDrawsMateAndStalemate() {
        for (fen in listOf("7k/8/6K1/8/8/8/8/8 b - - 0 1", // insufficient material
            "7k/8/6K1/8/8/8/8/R7 b - - 150 1", // automatic 75-move draw, even with a legal move
            "7k/6Q1/6K1/8/8/8/8/8 b - - 0 1", // mate
            "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")) { // stalemate
            assertFailsWith<IllegalArgumentException> { TacticsPackValidator.validatePuzzle(puzzle().copy(fen = fen, moves = listOf("h8g8", "a1a8"))) }
        }
    }
}
