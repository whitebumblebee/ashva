// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.content.AppPreferences
import com.openinglab.app.ui.*
import com.openinglab.app.ui.theme.OpeningLabTheme
import com.openinglab.shared.practice.StudyActivity
import com.openinglab.shared.storage.*
import com.openinglab.shared.tactics.TacticsAttempt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** UUID fixtures only; Task C compiles these and Claude Code runs them on the test device. */
@RunWith(AndroidJUnit4::class)
class DailyRoutineLearningTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun profileNode(tag: String): SemanticsNodeInteraction {
        rule.onNodeWithTag("learner-profile-screen").performScrollToNode(hasTestTag(tag))
        return rule.onNodeWithTag(tag)
    }
    private fun fixture(test: (RoomLearningStore, AppViewModel, AppPreferences) -> Unit) {
        val context = rule.activity.applicationContext
        val id = UUID.randomUUID().toString(); val dbName = "daily-routine-$id.db"
        val prefs = context.getSharedPreferences("daily-routine-$id", 0)
        val settings = AppPreferences(prefs)
        val db = createAndroidLearningDatabase(context, dbName); val store = RoomLearningStore(db); val owner = ViewModelStore()
        lateinit var vm: AppViewModel
        try {
            rule.runOnIdle {
                vm = AppViewModel(SavedStateHandle(), learningStore = store, appPreferences = settings); owner.put("routine", vm)
                rule.activity.setContent { OpeningLabTheme { OpeningLabApp(vm) } }
            }
            rule.waitUntil(10_000) { !vm.learner.state.value.loading }
            test(store, vm, settings)
        } finally { clearTestViewModels(owner) { rule.runOnIdle(it) }; db.close(); context.deleteDatabase(dbName); prefs.edit().clear().commit() }
    }
    @Test fun homeShowsTodayWithRealZeroProgressAndStudyWeek() = fixture { _, _, _ ->
        rule.onNodeWithTag("home-today").assertIsDisplayed()
        rule.onNodeWithText("Today").assertIsDisplayed()
        rule.onNodeWithText("Tactics · 20 puzzles").assertIsDisplayed()
        rule.onNodeWithTag("home-study-week").assertTextEquals("0 of 5 study days this week")
        rule.onNodeWithTag("routine-done").assertDoesNotExist()
    }
    @Test fun profilePuzzleTargetAndNamePersistAndUpdateHomeRoutine() = fixture { _, vm, prefs ->
        rule.onNodeWithText("Profile").performClick()
        profileNode("daily-puzzles-10").performClick()
        rule.waitUntil(10_000) { vm.learner.state.value.routine.settings.dailyPuzzles == 10 }
        assertEquals(10, prefs.learner().routine.dailyPuzzles)
        profileNode("profile-edit-name").performClick()
        rule.onNodeWithTag("profile-name-input").performTextInput("Ashva learner")
        rule.onNodeWithTag("profile-save-name").performClick()
        rule.onNodeWithText("Learn").performClick()
        rule.onNodeWithText("Tactics · 10 puzzles").assertIsDisplayed()
        rule.onNodeWithTag("home-greeting").assertTextContains("Ashva learner", substring = true)
        assertEquals("Ashva learner", prefs.learner().displayName)
    }
    @Test fun developerModeRevealsFeedbackAndProvenanceAlongsideSources() = fixture { _, vm, _ ->
        rule.onNodeWithText("Profile").performClick()
        profileNode("profile-sources").assertIsDisplayed()
        rule.onNodeWithTag("profile-content-feedback").assertDoesNotExist()
        rule.onNodeWithTag("profile-provenance").assertDoesNotExist()
        profileNode("developer-mode").performClick()
        rule.waitUntil(10_000) { vm.uiState.value.developerMode }
        profileNode("profile-sources").assertIsDisplayed()
        profileNode("profile-content-feedback").assertIsDisplayed()
        profileNode("profile-provenance").performClick()
        rule.onNodeWithText("Course text combines checked source moves", substring = true).assertExists()
        rule.onNodeWithText("docs/COURSE_PROVENANCE.md", substring = true).assertExists()
    }
    @Test fun missedPuzzleAndCompletedLineCountAsActivityWithoutInventedSolves() = fixture { store, vm, _ ->
        val now = System.currentTimeMillis()
        runBlocking {
            store.tactics.startCycle("woodpecker-easy", now)
            store.tactics.recordAttempt(TacticsAttempt("woodpecker-easy", 1, "fixture", 0, false, 100, now), listOf("fixture", "next"), 100)
            store.recordStudyActivity(StudyActivity("fixture", "ruy-lopez", "ruy-main", StudyActivity.PRACTICE, now))
        }
        rule.waitUntil(10_000) { vm.learner.state.value.routine.puzzles == 1 && vm.learner.state.value.routine.lines == 1 }
        assertEquals(1, vm.learner.state.value.week.studyDays)
        rule.onNodeWithText("Profile").performClick()
        rule.onNodeWithText("Puzzles solved").performScrollTo().assertExists()
        assertEquals(0, vm.learner.state.value.tactics.attempts.count { it.correct })
    }
    @Test fun actualStudyAndCompletedPracticeRecordLinesOnceWithoutCountingReviewAsALine() = fixture { _, vm, _ ->
        rule.runOnIdle {
            vm.startTrainer("ruy-lopez", com.openinglab.shared.model.PieceColor.WHITE, "ruy-main", study = true)
            vm.practiceTrainer()
        }
        var moves = 0
        while (vm.uiState.value.trainer?.isComplete == false && moves++ < 40) {
            rule.runOnIdle { vm.stayOnTrainerLine() }
            rule.waitUntil(10_000) { vm.uiState.value.trainer?.let {
                it.isComplete || (!it.isOpponentThinking && it.position.sideToMove == it.playerSide)
            } == true }
            if (vm.uiState.value.trainer?.isComplete == true) break
            rule.runOnIdle {
                val move = vm.uiState.value.trainer!!.replay.nextMove!!.move
                vm.trainerTap(move.from); vm.trainerTap(move.to)
            }
        }
        rule.waitUntil(10_000) { vm.learner.state.value.routine.lines == 1 && vm.learner.state.value.studiedLines == 1 }
        assertEquals(1, vm.learner.state.value.practisedLines)
        rule.runOnIdle { vm.pauseTrainer(); vm.setDeveloperMode(true); vm.pauseTrainer() }
        assertEquals(1, vm.learner.state.value.routine.lines)
    }
}
