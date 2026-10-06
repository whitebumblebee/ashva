// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.content.BundledContent
import com.openinglab.app.ui.*
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TeachingLearningTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @org.junit.After fun resetDeveloperMode() { rule.runOnIdle { ViewModelProvider(rule.activity)[AppViewModel::class.java].setDeveloperMode(false) } }
    private val vm get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]
    private fun ready() {
        rule.waitUntil(120_000) { vm.uiState.value.teachingOpenings.size == 149 || vm.uiState.value.catalogError != null }
        assertNull(vm.uiState.value.catalogError)
        assertEquals(149, vm.learningOpenings().size)
    }
    private fun click(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()

    @Test fun mainRuyEntryHasAllNamedRoutesAndDeeperBothColorTeaching() {
        ready()
        val summary = vm.learningOpenings().single { it.name == "Ruy Lopez" }
        assertEquals(235, summary.teaching!!.sourceRoutes)
        assertTrue(summary.teaching!!.authoredRoutes > 0)
        rule.onNodeWithTag("home-list").performScrollToNode(hasTestTag("opening-${summary.id}"))
        click("opening-${summary.id}")
        rule.waitUntil(60_000) { rule.onAllNodesWithTag("opening-detail").fetchSemanticsNodes().isNotEmpty() || vm.uiState.value.catalogError != null }
        assertNull(vm.uiState.value.catalogError)
        // Home carries lightweight summaries; opening the detail prepares the full course.
        val ruy = vm.getOpening(summary.id)
        assertTrue(ruy.variations.size > 235)
        assertEquals(235, ruy.variations.count { !it.authoredContinuation })
        assertEquals(summary.teaching!!.authoredRoutes, ruy.variations.count { it.authoredContinuation })
        rule.runOnIdle { vm.setDeveloperMode(true) }
        rule.onNodeWithTag("opening-detail").performScrollToNode(hasTestTag("teaching-coverage"))
        rule.onNodeWithTag("teaching-coverage").assertTextContains("235 named source routes", substring = true)
        val route = ruy.variations.first { it.id.endsWith(":berlin-ending") }
        rule.onNodeWithTag("opening-detail").performScrollToKey(route.id)
        click("route-${route.id}")
        rule.waitUntil(60_000) { vm.uiState.value.trainer?.opening?.id == ruy.id }
        click("study-mode")
        rule.runOnIdle { vm.jumpTrainer(16) }
        click("full-idea")
        assertEquals("Kxd8", vm.uiState.value.trainer!!.replay.lastMove!!.san)
        rule.onNodeWithTag("chosen-plan").performScrollTo().assertTextContains("bishop pair", substring = true)
        click("flip-side")
        assertEquals(PieceColor.BLACK, vm.uiState.value.trainer!!.playerSide)
        rule.onNodeWithTag("chosen-plan").performScrollTo().assertTextContains("king", substring = true)
        assertFalse(vm.uiState.value.trainer!!.variation.blackIdea.contains("not available"))
        rule.runOnIdle { vm.setDeveloperMode(false) }
    }

    @Test fun otherOpeningsHaveLongerLessonsHintsAndDeliberateBranches() {
        ready()
        for (family in listOf("London System", "Sicilian Defense", "French Defense", "Caro-Kann Defense", "Nimzo-Indian Defense")) {
            val opening = vm.learningOpenings().single { it.name == family }
            assertTrue(opening.teaching!!.authoredRoutes > 0)
            for (side in PieceColor.entries) {
                rule.runOnIdle { vm.startTrainer(opening.id, side) }
                rule.waitUntil(60_000) { vm.uiState.value.trainer?.opening?.id == opening.id && !vm.uiState.value.lessonLoading }
                rule.runOnIdle { vm.studyTrainer(); vm.jumpTrainer(5) }
                val before = vm.uiState.value.trainer!!
                assertTrue(before.replay.moves.size >= 15)
                assertFalse(before.variation.whiteIdea.contains("not available"))
                assertFalse(before.variation.blackIdea.contains("not available"))
                before.branchOffers.firstOrNull()?.let { branch ->
                    rule.runOnIdle { vm.switchTrainerBranch(branch.pathId, branch.targetPly); vm.returnTrainerBranch() }
                    assertEquals(before.position.toFen(), vm.uiState.value.trainer!!.position.toFen())
                }
            }
        }
        val french = vm.learningOpenings().single { it.name == "French Defense" }
        rule.runOnIdle { vm.startTrainer(french.id, PieceColor.WHITE) }
        rule.waitUntil(60_000) { vm.uiState.value.trainer?.opening?.id == french.id && !vm.uiState.value.lessonLoading }
        val board = vm.uiState.value.trainer!!.position
        rule.runOnIdle { vm.trainerTap("d2"); vm.trainerTap("d4") }
        assertEquals(board, vm.uiState.value.trainer!!.position)
        assertEquals(setOf("e2", "e4"), vm.uiState.value.trainer!!.hintSquares)
    }

    @Test fun courseColdResumePreservesBranchAndLegacySourceIdentity() {
        val context = rule.activity.applicationContext
        val name = "teaching-test-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name)
        val store = RoomLearningStore(db)
        runBlocking { store.install(BundledContent.read(context.assets, BundledContent.choices.first())) }
        val firstOwner = ViewModelStore(); val nextOwner = ViewModelStore()
        lateinit var first: AppViewModel
        rule.runOnIdle { first = AppViewModel(SavedStateHandle(), learningStore = store); firstOwner.put("fixture", first) }
        rule.waitUntil(120_000) { first.uiState.value.teachingOpenings.size == 149 }
        val ruy = first.learningOpenings().single { it.name == "Ruy Lopez" }
        val legacy = first.uiState.value.sourcedOpenings.single { it.name == "Ruy Lopez" }
        assertNotEquals(ruy.id, legacy.id)
        assertTrue(legacy.variations.all { it.whiteIdea.contains("not available") })
        rule.runOnIdle { first.startTrainer(ruy.id, PieceColor.BLACK) }
        rule.waitUntil(60_000) { first.uiState.value.trainer != null }
        rule.runOnIdle {
            first.studyTrainer(); first.jumpTrainer(5)
            val branch = first.uiState.value.trainer!!.branchOffers.first { it.nextMove.move.uci == "g8f6" }
            first.switchTrainerBranch(branch.pathId, branch.targetPly); first.jumpTrainer(8)
        }
        rule.waitUntil(10_000) { first.uiState.value.persistenceStatus == "Saved for offline resume" }
        val before = first.uiState.value.trainer!!
        clearTestViewModels(firstOwner) { rule.runOnIdle(it) }; db.close()
        val reopened = createAndroidLearningDatabase(context, name)
        try {
            lateinit var restored: AppViewModel
            rule.runOnIdle { restored = AppViewModel(SavedStateHandle(), learningStore = RoomLearningStore(reopened)); nextOwner.put("fixture", restored) }
            rule.waitUntil(120_000) { restored.uiState.value.trainer != null }
            val after = restored.uiState.value.trainer!!
            assertEquals(before.position, after.position)
            assertEquals(before.replay.pathId, after.replay.pathId)
            assertEquals(PieceColor.BLACK, after.playerSide)
            assertTrue(after.replay.canReturn)
        } finally {
            clearTestViewModels(nextOwner) { rule.runOnIdle(it) }; reopened.close(); context.deleteDatabase(name)
        }
    }
}
