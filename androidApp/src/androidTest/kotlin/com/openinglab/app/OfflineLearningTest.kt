package com.openinglab.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.assertTextContains
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.ui.AppViewModel
import com.openinglab.app.ui.LessonMode
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.storage.RoomLearningStore
import com.openinglab.shared.storage.createAndroidLearningDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Run only on an isolated emulator: the connected-test runner can uninstall its target APK. */
@RunWith(AndroidJUnit4::class)
class OfflineLearningTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]

    @Test fun bundledPacksInstallWithLicensesAndRestoreAfterRecreation() {
        rule.onNodeWithTag("offline-library").performScrollTo().performClick()
        for (source in listOf("lichess-openings", "lichess-broadcast-2020-04")) {
            rule.onNodeWithTag("offline-library-list").performScrollToKey(source)
            if (vm.uiState.value.packs.none { it.sourceId == source && it.state == "DOWNLOADED" })
                rule.onNodeWithTag("install-$source").performScrollTo().performClick()
            rule.waitUntil(120_000) { vm.uiState.value.packs.any { it.sourceId == source && it.state in listOf("DOWNLOADED", "ERROR") } }
            assertEquals(null, vm.uiState.value.packError)
            rule.onNodeWithTag("pack-status-$source").performScrollTo().assertTextContains("Installed for offline use")
        }
        assertEquals(setOf(3815, 79), vm.uiState.value.installedPacks.map { it.manifest.coverage.acceptedRecords }.toSet())
        assertEquals(setOf("CC0-1.0", "CC-BY-SA-4.0"), vm.uiState.value.installedPacks.map { it.manifest.source.license }.toSet())
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10_000) { vm.uiState.value.installedPacks.size == 2 }
        rule.onNodeWithTag("offline-library-list").performScrollToKey("lichess-broadcast-2020-04")
        rule.onNodeWithTag("pack-status-lichess-broadcast-2020-04").performScrollTo().assertTextContains("Installed for offline use")
    }

    @Test fun freshDatabaseAndViewModelRestoreBranchColorCursorAndReturnWithoutSavedBundle() {
        val context = rule.activity.applicationContext
        val name = "offline-learning-test-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name)
        val firstOwner = ViewModelStore(); val secondOwner = ViewModelStore()
        lateinit var first: AppViewModel
        rule.runOnIdle {
            first = AppViewModel(SavedStateHandle(), learningStore = RoomLearningStore(db))
            firstOwner.put("test", first)
            first.startTrainer("ruy-lopez", PieceColor.WHITE)
            first.studyTrainer(); first.jumpTrainer(5); first.switchTrainerBranch("ruy-berlin", 5)
            first.jumpTrainer(6); first.flipTrainerSide()
        }
        rule.waitUntil(10_000) { first.uiState.value.persistenceStatus == "Saved for offline resume" }
        val before = requireNotNull(first.uiState.value.trainer)
        rule.runOnIdle { firstOwner.clear() }
        db.close()
        val reopened = createAndroidLearningDatabase(context, name)
        try {
            lateinit var restored: AppViewModel
            rule.runOnIdle {
                restored = AppViewModel(SavedStateHandle(), learningStore = RoomLearningStore(reopened))
                secondOwner.put("test", restored)
            }
            rule.waitUntil(10_000) { restored.uiState.value.trainer != null }
            val after = requireNotNull(restored.uiState.value.trainer)
            assertEquals(before.position, after.position); assertEquals(6, after.ply)
            assertEquals(PieceColor.BLACK, after.playerSide); assertEquals(LessonMode.STUDY, after.mode)
            assertEquals("ruy-berlin", after.replay.pathId); assertTrue(after.replay.canReturn); assertFalse(after.isPlaying)
            rule.runOnIdle { restored.returnTrainerBranch() }
            rule.waitUntil(10_000) { restored.uiState.value.persistenceStatus == "Saved for offline resume" }
            assertEquals(5, restored.uiState.value.trainer?.ply)
            assertEquals("ruy-main", restored.uiState.value.trainer?.replay?.pathId)
            assertEquals("BLACK", runBlocking { RoomLearningStore(reopened).repertoires().single().side })
        } finally {
            rule.runOnIdle { secondOwner.clear() }
            reopened.close()
            context.deleteDatabase(name) // unique test-only file, never the application learning DB
        }
    }

    @Test fun wrongThenExpectedMoveRecordsAssistedAttemptAndColdHint() {
        val store = (rule.activity.application as OpeningLabApplication).learningStore
        rule.runOnIdle { vm.startTrainer("ruy-lopez", PieceColor.WHITE); vm.trainerTap("d2"); vm.trainerTap("d4") }
        rule.waitUntil(10_000) { vm.uiState.value.persistenceStatus == "Saved for offline resume" }
        val saved = runBlocking { store.latestBookmark() }!!
        assertTrue(saved.hint); assertEquals(0, saved.replay.ply); assertEquals("d2d4", saved.attemptUci)
        rule.runOnIdle { vm.trainerTap("e2"); vm.trainerTap("e4") }
        rule.waitUntil(10_000) { vm.uiState.value.trainer?.ply == 2 && vm.uiState.value.persistenceStatus == "Saved for offline resume" }
        val attempts = runBlocking { store.attempts("ruy-lopez") }
        assertTrue(attempts.any { it.moveUci == "d2d4" && it.outcome != "EXPECTED" })
        assertTrue(attempts.any { it.moveUci == "e2e4" && it.outcome == "EXPECTED" && it.assisted })
    }

    @Test fun homeContinueResumesSavedStudyBranchWithoutRestarting() {
        rule.runOnIdle {
            vm.startTrainer("ruy-lopez", PieceColor.WHITE); vm.studyTrainer(); vm.jumpTrainer(5)
            vm.switchTrainerBranch("ruy-berlin", 5); vm.jumpTrainer(6); vm.flipTrainerSide()
        }
        rule.waitUntil(10_000) { vm.uiState.value.persistenceStatus == "Saved for offline resume" }
        rule.onNodeWithTag("continue-lesson").performScrollTo().performClick()
        rule.onNodeWithTag("active-variation").assertTextContains("Berlin Defence")
        assertEquals(6, vm.uiState.value.trainer?.ply)
        assertEquals(PieceColor.BLACK, vm.uiState.value.trainer?.playerSide)
        assertEquals(LessonMode.STUDY, vm.uiState.value.trainer?.mode)
        assertTrue(requireNotNull(vm.uiState.value.trainer).replay.canReturn)
        assertFalse(requireNotNull(vm.uiState.value.trainer).isPlaying)
    }
}
