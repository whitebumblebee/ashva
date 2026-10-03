// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.content.BundledContent
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

/** Instrumentation can remove its target APK: use a disposable exact-serial emulator only. */
@RunWith(AndroidJUnit4::class)
class SourcedLearningTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]
    private fun installed() {
        rule.waitUntil(60_000) { vm.uiState.value.persistenceStatus != "Loading saved lesson…" }
        if (vm.uiState.value.sourcedOpenings.isEmpty()) rule.runOnIdle { vm.installPack("lichess-openings") }
        rule.waitUntil(120_000) { vm.uiState.value.sourcedOpenings.size == 149 || vm.uiState.value.packError != null || vm.uiState.value.catalogError != null }
        assertNull(vm.uiState.value.packError); assertNull(vm.uiState.value.catalogError)
        assertEquals(3815, vm.uiState.value.sourcedOpenings.sumOf { it.variations.size })
    }
    private fun click(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()

    @Test fun installedCatalogSearchRouteSelectionBothColorReplayAndBranchReturn() {
        installed()
        val ruy = vm.uiState.value.sourcedOpenings.single { it.name == "Ruy Lopez" }
        assertEquals(235, ruy.variations.size)
        val route = ruy.variations.filter { "Berlin" in it.name && it.steps.getOrNull(5)?.uci == "g8f6" }.maxBy { it.steps.size }
        rule.onNodeWithText("Explore").performClick()
        rule.runOnIdle { vm.updateSearch("Ruy"); vm.selectDifficulty("All") }
        rule.onNodeWithTag("opening-list").performScrollToNode(hasTestTag("opening-${vm.primaryOpeningId("ruy-lopez")}"))
        rule.onNodeWithTag("opening-${vm.primaryOpeningId("ruy-lopez")}").performScrollTo().performClick()
        click("explore-source-variations")
        rule.onNodeWithTag("opening-list").performScrollToIndex(0)
        assertEquals("Sourced", vm.uiState.value.selectedDifficulty)
        rule.onNodeWithTag("source-catalog-status").assertTextContains("149 source families · 3815 routes", substring = true)
        click("opening-${ruy.id}")
        rule.onNodeWithTag("opening-detail").performScrollToNode(hasTestTag("route-search"))
        rule.onNodeWithTag("route-search").performTextInput(route.name)
        click("route-black")
        rule.onNodeWithTag("opening-detail").performScrollToKey(route.id)
        click("route-${route.id}")
        rule.waitUntil(60_000) { vm.uiState.value.trainer != null && !vm.uiState.value.lessonLoading }
        click("study-mode")
        val trainer = requireNotNull(vm.uiState.value.trainer)
        assertEquals(PieceColor.BLACK, trainer.playerSide); assertEquals(route.id, trainer.replay.pathId)
        assertTrue(trainer.replay.moves.size > 16)
        rule.onNodeWithTag("source-coverage").assertTextContains("SOURCED · CC0-1.0", substring = true)
        rule.runOnIdle { vm.jumpTrainer(5) }
        val before = requireNotNull(vm.uiState.value.trainer)
        if (before.branchOffers.size > 12) {
            rule.onNodeWithTag("branch-count").performScrollTo().assertTextContains("Showing 12 of", substring = true)
            click("more-branches")
            rule.onNodeWithTag("branch-count").performScrollTo().assertTextContains("Showing 24 of", substring = true)
        }
        val branch = before.branchOffers.first()
        click("branch-${branch.pathId}")
        click("replay-next")
        assertEquals(branch.nextMove.move, vm.uiState.value.trainer?.replay?.lastMove?.move)
        click("return-branch")
        assertEquals(before.position, vm.uiState.value.trainer?.position)
        assertEquals(route.id, vm.uiState.value.trainer?.replay?.pathId)
        click("flip-side"); assertEquals(PieceColor.WHITE, vm.uiState.value.trainer?.playerSide)
        rule.onNodeWithTag("chosen-plan").performScrollTo().assertTextContains("not available", substring = true)
    }

    @Test fun sourcePracticeKeepsWrongMoveAndSourceIdentifierHandlesTransposition() {
        installed()
        val ruy = vm.uiState.value.sourcedOpenings.single { it.name == "Ruy Lopez" }
        rule.runOnIdle { vm.startTrainer(ruy.id, PieceColor.WHITE) }
        rule.waitUntil(60_000) { vm.uiState.value.trainer != null && !vm.uiState.value.lessonLoading }
        val before = requireNotNull(vm.uiState.value.trainer).position
        rule.runOnIdle { vm.trainerTap("d2"); vm.trainerTap("d4") }
        assertEquals(before, vm.uiState.value.trainer?.position)
        assertEquals(setOf("e2", "e4"), vm.uiState.value.trainer?.hintSquares)
        rule.runOnIdle { vm.importIdentifier("1. c4 Nf6 2. d4 e6 3. Nc3 Bb4 *", false) }
        rule.waitUntil(30_000) { !vm.uiState.value.identifier.isLoading && vm.uiState.value.identifier.match.candidates.any { it.provenance != null } }
        assertNull(vm.uiState.value.identifier.error)
        assertTrue(vm.uiState.value.identifier.match.candidates.any { it.name == "Nimzo-Indian Defense" })
        assertEquals(6, vm.uiState.value.identifier.match.matchedPly)
    }

    @Test fun reopenedDatabaseRestoresSourcedGraphBranchColorAndCursorWithoutSavedBundle() {
        val context = rule.activity.applicationContext
        val name = "source-learning-test-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name)
        val store = RoomLearningStore(db)
        runBlocking { store.install(BundledContent.read(context.assets, BundledContent.choices.first())) }
        val firstOwner = ViewModelStore(); val secondOwner = ViewModelStore()
        lateinit var first: AppViewModel
        rule.runOnIdle { first = AppViewModel(SavedStateHandle(), learningStore = store); firstOwner.put("test", first) }
        rule.waitUntil(60_000) { first.uiState.value.sourcedOpenings.size == 149 }
        val ruy = first.uiState.value.sourcedOpenings.single { it.name == "Ruy Lopez" }
        val morphy = ruy.variations.first { it.steps.getOrNull(5)?.uci == "a7a6" && it.steps.size > 6 }
        rule.runOnIdle { first.startTrainer(ruy.id, PieceColor.WHITE, morphy.id) }
        rule.waitUntil(60_000) { first.uiState.value.trainer != null }
        rule.runOnIdle {
            first.studyTrainer(); first.jumpTrainer(5)
            val berlin = requireNotNull(first.uiState.value.trainer).branchOffers.first { it.nextMove.move.uci == "g8f6" }
            first.switchTrainerBranch(berlin.pathId, berlin.targetPly); first.jumpTrainer(6); first.flipTrainerSide()
        }
        rule.waitUntil(10_000) { first.uiState.value.persistenceStatus == "Saved for offline resume" }
        val before = requireNotNull(first.uiState.value.trainer)
        clearTestViewModels(firstOwner) { rule.runOnIdle(it) }; db.close()
        val reopened = createAndroidLearningDatabase(context, name)
        try {
            lateinit var restored: AppViewModel
            rule.runOnIdle { restored = AppViewModel(SavedStateHandle(), learningStore = RoomLearningStore(reopened)); secondOwner.put("test", restored) }
            rule.waitUntil(60_000) { restored.uiState.value.trainer != null }
            val after = requireNotNull(restored.uiState.value.trainer)
            assertEquals(before.position, after.position); assertEquals(before.replay.pathId, after.replay.pathId)
            assertEquals(PieceColor.BLACK, after.playerSide); assertEquals(6, after.ply)
            assertEquals(LessonMode.STUDY, after.mode); assertTrue(after.replay.canReturn)
            rule.runOnIdle { restored.returnTrainerBranch() }
            assertEquals(morphy.id, restored.uiState.value.trainer?.replay?.pathId)
            assertEquals(5, restored.uiState.value.trainer?.ply)
        } finally {
            clearTestViewModels(secondOwner) { rule.runOnIdle(it) }; reopened.close()
            context.deleteDatabase(name) // The UUID-named test fixture only, never the learner database.
        }
    }
}
