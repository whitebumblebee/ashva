// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.review

import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.data.OpeningCatalog
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.repertoire.RepertoireBook
import com.openinglab.shared.storage.LearningAttempt
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class RecallTest {
    private val book = RepertoireBook(LessonGraph.fromOpening(OpeningCatalog.byId("ruy-lopez")))
    @Test fun chosenRoutesAndBothColorsHaveRealDenominators() {
        for (side in PieceColor.entries) {
            val policy = book.seed(side, "ruy-main")
            val enrollment = RecallPlanner.policy(book, policy)
            enrollment.validate()
            assertEquals(book.graph.paths.getValue("ruy-main").positions.dropLast(1).count { it.sideToMove == side }, enrollment.entries.size)
            assertEquals(setOf("ruy-main"), enrollment.entries.map { it.target.pathId }.toSet())
            assertTrue(enrollment.entries.all { it.target.side == side })
        }
    }
    @Test fun expandedBranchPreservesSharedPrefixCardsButPinsNewScopeRevision() {
        val policy = book.seed(PieceColor.WHITE, "ruy-main")
        val first = RecallPlanner.policy(book, policy)
        val expanded = RecallPlanner.policy(book, book.adoptRoute(policy, "ruy-berlin"))
        assertNotEquals(first.scope.id, expanded.scope.id)
        assertEquals(first.scope.groupId, expanded.scope.groupId)
        assertTrue(expanded.entries.size > first.entries.size)
        assertTrue(first.entries.all { old -> expanded.entries.any { it.target.id == old.target.id } })
        assertEquals(1, expanded.entries.count { it.target.ply == 0 })
    }
    @Test fun transpositionsAndRepeatedBoardsDoNotEraseHistory() {
        val graph = LessonGraph.fromPgn(Pgn.parse("1. Nf3 (1. d4 d5 2. Nf3 Nf6 3. Bf4) Nf6 2. d4 d5 3. Bf4 *"))
        val b = RepertoireBook(graph)
        val entries = RecallPlanner.entries(b, PieceColor.WHITE, graph.paths.keys.toList())
        assertEquals(2, entries.count { it.target.ply == 4 })
        assertNotEquals(entries.filter { it.target.ply == 4 }[0].target.id, entries.filter { it.target.ply == 4 }[1].target.id)
        val repeated = RepertoireBook(LessonGraph.fromPgn(Pgn.parse("1. Nf3 Nf6 2. Ng1 Ng8 3. Nf3 Nf6 *")))
        val cards = RecallPlanner.entries(repeated, PieceColor.WHITE, listOf("original"))
        assertNotEquals(cards[0].target.id, cards[2].target.id)
    }
    @Test fun narrationDoesNotResetCardsButMoveSnapshotAndColorDo() {
        val opening = OpeningCatalog.byId("ruy-lopez")
        val edited = RepertoireBook(LessonGraph.fromOpening(opening.copy(variations = opening.variations.map { it.copy(description = "New prose") })))
        assertEquals(RecallPlanner.route(book, PieceColor.WHITE, "ruy-main").entries.map { it.target.id },
            RecallPlanner.route(edited, PieceColor.WHITE, "ruy-main").entries.map { it.target.id })
        val smaller = RepertoireBook(LessonGraph.fromOpening(opening.copy(variations = opening.variations.filter { it.id == "ruy-main" })))
        assertNotEquals(RecallPlanner.route(book, PieceColor.WHITE, "ruy-main").scope.id, RecallPlanner.route(smaller, PieceColor.WHITE, "ruy-main").scope.id)
        assertNotEquals(RecallPlanner.route(book, PieceColor.WHITE, "ruy-main").scope.id, RecallPlanner.route(book, PieceColor.BLACK, "ruy-main").scope.id)
    }
    @Test fun dueRecallExtendsOnlyAtDeterministicSeparatedIntervals() {
        var state = RecallState()
        val start = 1_000_000L
        var at = start
        for (days in RecallScheduler.intervalsDays) {
            state = RecallScheduler.grade(state, RecallGrade.UNAIDED, at)
            assertEquals(at + days * RecallScheduler.DAY, state.dueAt)
            at = state.dueAt
        }
        assertEquals(8, state.spacedSuccesses)
        assertEquals(state, Json.decodeFromString<RecallState>(Json.encodeToString(state)))
    }
    @Test fun hintsAndFailuresResetSpacingAndEarlyDrillsCannotInflateIt() {
        val first = RecallScheduler.grade(RecallState(), RecallGrade.UNAIDED, 1_000_000)
        val drill = RecallScheduler.grade(first, RecallGrade.UNAIDED, 2_000_000)
        assertEquals(first.dueAt, drill.dueAt); assertEquals(1, drill.spacedSuccesses)
        for (grade in listOf(RecallGrade.ASSISTED, RecallGrade.NOT_RECALLED)) {
            val failed = RecallScheduler.grade(drill, grade, 3_000_000)
            assertEquals(0, failed.spacedSuccesses)
            assertEquals(3_000_000 + RecallScheduler.RETRY, failed.dueAt)
            val immediate = RecallScheduler.grade(failed, RecallGrade.UNAIDED, 3_000_001)
            assertEquals(0, immediate.spacedSuccesses); assertEquals(failed.dueAt, immediate.dueAt)
        }
    }
    @Test fun backwardsClockInvalidStateAndTimestampOverflowFailSafely() {
        val old = RecallScheduler.grade(RecallState(), RecallGrade.UNAIDED, 1_000_000)
        assertEquals(old, RecallScheduler.grade(old, RecallGrade.NOT_RECALLED, 999_999))
        assertFails { RecallScheduler.grade(old, RecallGrade.UNAIDED, -1) }
        assertFails { RecallScheduler.grade(old.copy(schedulerVersion = 99), RecallGrade.UNAIDED, 2_000_000) }
        assertEquals(RecallScheduler.MAX_TIME, RecallScheduler.grade(RecallState(), RecallGrade.UNAIDED, RecallScheduler.MAX_TIME - 1).dueAt)
    }
    @Test fun forgedIllegalOrMismatchedGradeIsNeverAccepted() {
        val enrollment = RecallPlanner.route(book, PieceColor.WHITE, "ruy-main")
        val target = enrollment.entries.first().target
        val attempt = LearningAttempt("real-test", target.lessonId, target.pathId, 0, "WHITE", "e2e4", "EXPECTED", false, 1_000_000)
        val event = RecallEvent(attempt, target, RecallGrade.UNAIDED, scopeId = enrollment.scope.id)
        event.validate()
        assertFails { event.copy(help = setOf(RecallHelp.ENGINE)).validate() }
        assertFails { event.copy(attempt = attempt.copy(moveUci = "d2d4")).validate() }
        assertFails { target.copy(expectedUci = "e2e5").let { it.copy(id = it.identity()) }.validate() }
        assertFails { enrollment.copy(entries = enrollment.entries + enrollment.entries.first()).validate() }
    }
}
