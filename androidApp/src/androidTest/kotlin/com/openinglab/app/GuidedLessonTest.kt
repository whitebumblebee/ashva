package com.openinglab.app

import android.os.Bundle
import android.os.Parcel
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.ui.AppViewModel
import com.openinglab.app.ui.LessonMode
import com.openinglab.shared.model.PieceColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuidedLessonTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]
    private val trainer get() = requireNotNull(vm.uiState.value.trainer)

    private fun open(side: PieceColor = PieceColor.WHITE) {
        rule.onNodeWithText("Explore").performClick()
        rule.onNodeWithText("Starter").performClick()
        rule.onNodeWithText("Ruy López").performScrollTo().performClick()
        rule.onNodeWithText(if (side == PieceColor.WHITE) "Play White" else "Play Black").performScrollTo().performClick()
        rule.onNodeWithTag("study-mode").assertIsDisplayed()
    }

    private fun click(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
    private fun awaitPly(ply: Int) = rule.waitUntil(10_000) { trainer.ply == ply && !trainer.isOpponentThinking }
    private fun move(from: String, to: String) {
        click("square-$from"); click("square-$to")
    }

    @Test fun legalWrongAttemptAutomaticallyShowsHintAndKeepsBoard() {
        open()
        val before = trainer.position
        move("d2", "d4")
        rule.onNodeWithTag("expected-move").performScrollTo().assertTextContains("Expected: e4", substring = true)
        rule.onNodeWithTag("lesson-feedback").assertTextContains("Legal move, but outside this lesson", substring = true)
        rule.onNodeWithTag("square-e2").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expected move"))
        assertEquals(before, trainer.position)
        assertEquals(0, trainer.ply)
        move("e2", "e4")
        awaitPly(2)
        assertEquals(1, trainer.assistedMoves)
    }

    @Test fun illegalAttemptExplainsLegalityAndShowsExpectedMove() {
        open()
        move("e2", "e5")
        rule.onNodeWithTag("lesson-feedback").performScrollTo().assertTextContains("not legal here", substring = true)
        rule.onNodeWithTag("expected-move").assertTextContains("e2 → e4", substring = true)
        assertEquals(0, trainer.ply)
    }

    @Test fun fullIdeaHasCompleteNotationPlansAndDeterministicControls() {
        open()
        click("study-mode")
        click("full-idea")
        rule.onNodeWithTag("full-idea").assertTextContains("Plans for both sides", substring = true)
        rule.onNodeWithTag("chosen-plan").assertTextContains("c3–d4", substring = true)
        rule.onNodeWithTag("move-16").performScrollTo().assertTextContains("8... O-O")
        click("move-16")
        assertEquals(16, trainer.ply)
        click("replay-first"); assertEquals(0, trainer.ply)
        click("replay-next"); assertEquals(1, trainer.ply)
        click("replay-previous"); assertEquals(0, trainer.ply)
        click("move-4"); assertEquals(4, trainer.ply)
        click("replay-last"); assertEquals(16, trainer.ply)
        click("flip-side"); assertEquals(PieceColor.BLACK, trainer.playerSide)
        rule.onNodeWithTag("chosen-plan").performScrollTo().assertTextContains("...a6", substring = true)
    }

    @Test fun studyBranchAndReturnRestorePathPositionAndColor() {
        open(PieceColor.BLACK)
        awaitPly(1)
        click("study-mode")
        click("move-5")
        val before = trainer.position
        rule.onNodeWithTag("branch-choice").performScrollTo().assertIsDisplayed()
        click("branch-ruy-berlin")
        assertEquals("ruy-berlin", trainer.replay.pathId)
        assertEquals(before, trainer.position)
        click("replay-next"); assertEquals("Nf6", trainer.replay.lastMove?.san)
        click("return-branch")
        assertEquals("ruy-main", trainer.replay.pathId)
        assertEquals(5, trainer.ply)
        assertEquals(before, trainer.position)
        assertEquals(PieceColor.BLACK, trainer.playerSide)
    }

    @Test fun blackAlternateAttemptAppliesOnlyAfterConfirmedSwitch() {
        open(PieceColor.BLACK)
        awaitPly(1)
        move("e7", "e5"); awaitPly(3)
        move("b8", "c6"); awaitPly(5)
        val before = trainer.position
        move("g8", "f6")
        assertEquals(5, trainer.ply)
        assertEquals(before, trainer.position)
        assertEquals("ruy-main", trainer.replay.pathId)
        rule.onNodeWithTag("expected-move").performScrollTo().assertTextContains("a6", substring = true)
        click("branch-ruy-berlin")
        awaitPly(7)
        assertEquals("ruy-berlin", trainer.replay.pathId)
        assertEquals("g8f6", trainer.replay.moves[5].move.uci)
        click("return-branch")
        assertEquals(5, trainer.ply)
        assertEquals(before, trainer.position)
    }

    @Test fun automaticReplyWaitsForStayAtBranchPoint() {
        open()
        move("e2", "e4"); awaitPly(2)
        move("g1", "f3"); awaitPly(4)
        move("f1", "b5")
        assertEquals(5, trainer.ply)
        assertFalse(trainer.isOpponentThinking)
        assertTrue(trainer.branchOffers.any { it.pathId == "ruy-berlin" })
        click("stay-line")
        awaitPly(6)
        assertEquals("a6", trainer.replay.lastMove?.san)
        assertTrue(trainer.branchOffers.any { it.pathId == "ruy-exchange" })
    }

    @Test fun autoplayPausesAtBranchAndBackgroundCancelsPlayback() {
        open()
        click("study-mode")
        rule.runOnIdle { vm.changeTrainerSpeed(); vm.changeTrainerSpeed() } // 700 ms
        click("replay-play")
        rule.waitUntil(12_000) { trainer.ply == 5 && !trainer.isPlaying }
        assertTrue(trainer.branchOffers.isNotEmpty())
        click("stay-line")
        click("replay-play")
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(trainer.isPlaying)
        val stopped = trainer.ply
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        val deadline = SystemClock.elapsedRealtime() + 900
        rule.waitUntil(3_000) { SystemClock.elapsedRealtime() >= deadline }
        assertEquals(stopped, trainer.ply)
        assertFalse(trainer.isPlaying)
    }

    @Test fun activityRecreationRestoresNavigationCursorColorAndBranchReturn() {
        open()
        click("study-mode"); click("move-5"); click("branch-ruy-berlin")
        click("replay-next"); click("flip-side")
        val position = trainer.position
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("active-variation").assertTextContains("Berlin Defence")
        assertEquals(6, trainer.ply)
        assertEquals(PieceColor.BLACK, trainer.playerSide)
        assertEquals(position, trainer.position)
        click("return-branch")
        assertEquals(5, trainer.ply)
    }

    @Test fun savedBundleRebuildsFreshViewModelIncludingPendingHintAndBranches() {
        rule.runOnIdle {
            val handle = SavedStateHandle()
            val first = AppViewModel(handle)
            first.startTrainer("ruy-lopez", PieceColor.WHITE)
            first.studyTrainer(); first.jumpTrainer(5)
            first.switchTrainerBranch("ruy-berlin", 5)
            first.jumpTrainer(6); first.flipTrainerSide()
            val parcel = Parcel.obtain()
            val saved = requireNotNull(handle.get<Bundle>("trainer_session"))
            parcel.writeBundle(saved); parcel.setDataPosition(0)
            val copied = requireNotNull(parcel.readBundle(javaClass.classLoader)); parcel.recycle()
            val restored = AppViewModel(SavedStateHandle(mapOf("trainer_session" to copied)))
            val state = requireNotNull(restored.uiState.value.trainer)
            assertEquals(first.uiState.value.trainer?.position, state.position)
            assertEquals(PieceColor.BLACK, state.playerSide)
            assertEquals(LessonMode.STUDY, state.mode)
            assertEquals("ruy-berlin", state.replay.pathId)
            assertTrue(state.replay.canReturn)
            assertFalse(state.isPlaying)
            restored.returnTrainerBranch(); assertEquals(5, restored.uiState.value.trainer?.ply)

            first.startTrainer("ruy-lopez", PieceColor.WHITE)
            first.trainerTap("d2"); first.trainerTap("d4")
            val hinted = AppViewModel(SavedStateHandle(mapOf("trainer_session" to handle.get<Bundle>("trainer_session"))))
            assertEquals(setOf("e2", "e4"), hinted.uiState.value.trainer?.hintSquares)
            assertEquals(0, hinted.uiState.value.trainer?.ply)
            copied.putInt("content", -1)
            assertNull(AppViewModel(SavedStateHandle(mapOf("trainer_session" to copied))).uiState.value.trainer)
            first.pauseTrainer(); restored.pauseTrainer(); hinted.pauseTrainer()
        }
    }

    @Test fun completingStudiedPracticeReportsAssistanceNotMastery() {
        open()
        click("study-mode"); click("practice-mode")
        click("show-hint")
        while (!trainer.isComplete) {
            if (trainer.branchOffers.isNotEmpty()) click("stay-line")
            if (trainer.isComplete) break
            if (trainer.position.sideToMove == trainer.playerSide) {
                val expected = requireNotNull(trainer.replay.nextMove).move
                move(expected.from, expected.to)
            } else {
                val ply = trainer.ply
                rule.waitUntil(10_000) { trainer.ply > ply || !trainer.isOpponentThinking }
            }
        }
        assertEquals(16, trainer.ply)
        assertEquals(8, trainer.assistedMoves)
        assertTrue(trainer.hasStudied)
        rule.onNodeWithText("0 retries · 8 assisted moves").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("You studied this line first. Completion is not a mastery score.").performScrollTo().assertIsDisplayed()
    }

    @Test fun pendingAlternativeRestoresWithoutApplyingAndCanBeConfirmed() {
        val handle = SavedStateHandle()
        lateinit var first: AppViewModel
        rule.runOnIdle { first = AppViewModel(handle); first.startTrainer("ruy-lopez", PieceColor.BLACK) }
        rule.waitUntil(5_000) { first.uiState.value.trainer?.ply == 1 }
        rule.runOnIdle { first.trainerTap("e7"); first.trainerTap("e5") }
        rule.waitUntil(5_000) { first.uiState.value.trainer?.ply == 3 }
        rule.runOnIdle { first.trainerTap("b8"); first.trainerTap("c6") }
        rule.waitUntil(5_000) { first.uiState.value.trainer?.ply == 5 }
        rule.runOnIdle {
            first.trainerTap("g8"); first.trainerTap("f6")
            val before = requireNotNull(first.uiState.value.trainer).position
            val restored = AppViewModel(SavedStateHandle(mapOf("trainer_session" to handle.get<Bundle>("trainer_session"))))
            val pending = requireNotNull(restored.uiState.value.trainer)
            assertEquals(before, pending.position)
            assertEquals(5, pending.ply)
            assertEquals("ruy-main", pending.replay.pathId)
            assertEquals("ruy-berlin", pending.branchOffers.single().pathId)
            assertEquals(setOf("a7", "a6"), pending.hintSquares)
            restored.switchTrainerBranch("ruy-berlin", 5)
            restored.pauseTrainer()
            assertEquals(6, restored.uiState.value.trainer?.ply)
            assertEquals("g8f6", restored.uiState.value.trainer?.position?.lastMove?.uci)
            restored.returnTrainerBranch()
            assertEquals(before, restored.uiState.value.trainer?.position)
            first.pauseTrainer(); restored.pauseTrainer()
        }
    }

    @Test fun manualStudyNavigationCancelsQueuedOpponentReply() {
        open()
        rule.runOnIdle {
            vm.startTrainer("ruy-lopez", PieceColor.BLACK)
            vm.studyTrainer()
            vm.jumpTrainer(3)
        }
        val before = trainer.position
        val deadline = SystemClock.elapsedRealtime() + 800
        rule.waitUntil(3_000) { SystemClock.elapsedRealtime() >= deadline }
        assertEquals(3, trainer.ply)
        assertEquals(before, trainer.position)
        assertEquals(LessonMode.STUDY, trainer.mode)
        assertFalse(trainer.isOpponentThinking)
    }
}
