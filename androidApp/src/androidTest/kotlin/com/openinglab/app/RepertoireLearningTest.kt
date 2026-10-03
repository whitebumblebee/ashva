// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.content.BundledContent
import com.openinglab.app.ui.AppViewModel
import com.openinglab.app.ui.OpeningLabApp
import com.openinglab.app.ui.theme.OpeningLabTheme
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.repertoire.RepertoireGapKind
import com.openinglab.shared.storage.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Exact-serial disposable emulator only. Each case uses a unique test database. */
@RunWith(AndroidJUnit4::class)
class RepertoireLearningTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun editorNavigationAndCursorSurviveActivityRecreation() {
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        rule.waitUntil(20_000) { vm.uiState.value.persistenceStatus != "Loading saved lesson…" }
        rule.onNodeWithText("Explore").performClick()
        rule.onNodeWithText("Starter").performClick()
        rule.onNodeWithText("Ruy López").performScrollTo().performClick()
        rule.onNodeWithText("Play Black").performScrollTo().performClick()
        rule.runOnIdle { vm.studyTrainer(); vm.jumpTrainer(5) }
        click("build-repertoire")
        rule.waitUntil(20_000) { vm.uiState.value.repertoireEditor != null }
        rule.runOnIdle { vm.moveRepertoireCursor("ruy-main", 5) }
        rule.waitUntil(20_000) { vm.uiState.value.repertoireEditor?.ply == 5 }
        val policy = vm.uiState.value.repertoireEditor!!.policy
        rule.activityRule.scenario.recreate()
        rule.waitUntil(20_000) { vm.uiState.value.repertoireEditor?.ply == 5 && !vm.uiState.value.repertoireLoading }
        rule.onNodeWithTag("repertoire-position").performScrollTo().assertTextContains("Position 5/16", substring = true)
        assertEquals(policy, vm.uiState.value.repertoireEditor!!.policy)
        assertEquals(PieceColor.BLACK, vm.uiState.value.repertoireEditor!!.policy.side)
    }

    private fun fixture(test: (AppViewModel, RoomLearningStore, LearningDatabase) -> Unit) {
        val context = rule.activity.applicationContext
        val name = "repertoire-test-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name)
        val store = RoomLearningStore(db)
        val owner = ViewModelStore()
        lateinit var vm: AppViewModel
        try {
            rule.runOnIdle {
                vm = AppViewModel(SavedStateHandle(), learningStore = store)
                owner.put("test", vm)
                rule.activity.setContent { OpeningLabTheme { OpeningLabApp(vm) } }
            }
            rule.waitUntil(15_000) { vm.uiState.value.persistenceStatus != "Loading saved lesson…" }
            test(vm, store, db)
        } finally {
            clearTestViewModels(owner) { rule.runOnIdle(it) }
            db.close(); context.deleteDatabase(name)
        }
    }

    private fun click(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
    private fun editorClick(key: String, tag: String) {
        rule.onNodeWithTag("repertoire-scroll").performScrollToKey(key)
        click(tag)
    }
    private fun revision(vm: AppViewModel, value: Int) = rule.waitUntil(20_000) {
        vm.uiState.value.repertoireEditor?.let { it.policy.revision == value && !it.saving } == true
    }

    @Test fun rapidColorNavigationLoadsTheLastRequestedPolicy() = fixture { vm, store, _ ->
        rule.runOnIdle {
            vm.openRepertoire("ruy-lopez", PieceColor.WHITE, "ruy-main", 5)
            vm.openRepertoire("ruy-lopez", PieceColor.BLACK, "ruy-main", 5)
        }
        rule.waitUntil(20_000) { vm.uiState.value.repertoireEditor?.policy?.side == PieceColor.BLACK && !vm.uiState.value.repertoireLoading }
        val editor = vm.uiState.value.repertoireEditor!!
        assertEquals(PieceColor.BLACK, editor.policy.side)
        rule.runOnIdle { vm.chooseRepertoireMove("g8f6", true); vm.openRepertoire("ruy-lopez", PieceColor.WHITE, "ruy-main", 5) }
        rule.waitUntil(20_000) { vm.uiState.value.repertoireEditor?.policy?.side == PieceColor.WHITE && !vm.uiState.value.repertoireLoading }
        val savedBlack = runBlocking { store.repertoirePolicy(editor.policy.id) }!!
        assertEquals(1, savedBlack.revision)
        assertEquals("g8f6", savedBlack.preferredMoves[editor.position.positionKey])
        assertEquals(PieceColor.WHITE, vm.uiState.value.repertoireEditor!!.policy.side)
    }

    @Test fun whiteEditorIncludesBerlinShowsGapAndPracticesOnlyChosenScope() = fixture { vm, _, _ ->
        rule.onNodeWithText("Explore").performClick()
        rule.onNodeWithText("Starter").performClick()
        rule.onNodeWithText("Ruy López").performScrollTo().performClick()
        rule.onNodeWithText("Play White").performScrollTo().performClick()
        rule.runOnIdle { vm.studyTrainer(); vm.jumpTrainer(5) }
        click("build-repertoire")
        rule.waitUntil(20_000) { vm.uiState.value.repertoireEditor != null }
        rule.onNodeWithTag("repertoire-coverage").assertTextContains("1 recorded routes", substring = true)
        editorClick("option:g8f6", "repertoire-option-g8f6")
        revision(vm, 1)
        val gap = vm.uiState.value.repertoireEditor!!.coverage.gaps.single()
        assertEquals(RepertoireGapKind.CHOOSE_LEARNER_MOVE, gap.kind); assertEquals(6, gap.ply)
        editorClick("gap:${gap.pathId}:${gap.ply}:${gap.kind}:${gap.moveUci}", "repertoire-gap-6")
        rule.waitUntil(10_000) { vm.uiState.value.repertoireEditor?.ply == 6 }
        editorClick("option:e1g1", "repertoire-option-e1g1")
        revision(vm, 2)
        rule.onNodeWithTag("repertoire-scroll").performScrollToIndex(2)
        click("repertoire-adopt-route"); rule.onNodeWithTag("confirm-adopt-route").performClick()
        revision(vm, 3)
        assertEquals(setOf("ruy-main", "ruy-berlin"), vm.uiState.value.repertoireEditor!!.coverage.eligiblePathIds.toSet())
        rule.onNodeWithTag("repertoire-scroll").performScrollToIndex(1)
        click("practice-repertoire")
        rule.waitUntil(20_000) { vm.uiState.value.trainer?.repertoirePolicy != null && !vm.uiState.value.lessonLoading }
        rule.onNodeWithTag("policy-practice-scope").assertTextContains("revision 3", substring = true)
        rule.onNodeWithTag("flip-side").assertIsNotEnabled()
        rule.runOnIdle { vm.studyTrainer(); vm.jumpTrainer(5) }
        val trainer = vm.uiState.value.trainer!!
        assertEquals(setOf("ruy-main", "ruy-berlin"), trainer.replay.graph.paths.keys)
        assertEquals(1, trainer.branchOffers.size)
        click("branch-ruy-berlin"); click("replay-next"); click("return-branch")
        assertEquals(5, vm.uiState.value.trainer!!.ply)
        assertEquals(PieceColor.WHITE, vm.uiState.value.trainer!!.playerSide)
    }

    @Test fun blackPreferredMoveExcludesBerlinWithoutCallingItIllegal() = fixture { vm, _, _ ->
        rule.onNodeWithText("Explore").performClick()
        rule.onNodeWithText("Starter").performClick()
        rule.onNodeWithText("Ruy López").performScrollTo().performClick()
        rule.onNodeWithText("Play Black").performScrollTo().performClick()
        rule.runOnIdle { vm.studyTrainer(); vm.jumpTrainer(5) }
        assertTrue(vm.uiState.value.trainer!!.branchOffers.any { it.pathId == "ruy-berlin" })
        click("build-repertoire")
        rule.waitUntil(20_000) { vm.uiState.value.repertoireEditor != null }
        rule.onNodeWithTag("repertoire-scroll").performScrollToIndex(1)
        click("practice-repertoire")
        rule.waitUntil(20_000) { vm.uiState.value.trainer?.repertoirePolicy != null }
        rule.waitUntil(10_000) { vm.uiState.value.trainer?.ply == 1 && vm.uiState.value.trainer?.isOpponentThinking == false }
        rule.runOnIdle { vm.trainerTap("e7"); vm.trainerTap("e5") }
        rule.waitUntil(10_000) { vm.uiState.value.trainer?.ply == 3 && vm.uiState.value.trainer?.isOpponentThinking == false }
        rule.runOnIdle { vm.trainerTap("b8"); vm.trainerTap("c6") }
        rule.waitUntil(10_000) { vm.uiState.value.trainer?.ply == 5 && vm.uiState.value.trainer?.isOpponentThinking == false }
        assertTrue(vm.uiState.value.trainer!!.branchOffers.isEmpty())
        assertEquals("a7a6", vm.uiState.value.trainer!!.replay.nextMove!!.move.uci)
        val before = vm.uiState.value.trainer!!.position
        rule.runOnIdle { vm.trainerTap("g8"); vm.trainerTap("f6") }
        assertEquals(before, vm.uiState.value.trainer!!.position)
        assertTrue(vm.uiState.value.trainer!!.feedback.contains("Legal move"))
        assertTrue(vm.uiState.value.trainer!!.branchOffers.isEmpty())
        rule.runOnIdle { vm.flipTrainerSide() }
        assertEquals(PieceColor.BLACK, vm.uiState.value.trainer!!.playerSide)
        // Return to unrestricted source/seed exploration, which must still expose Berlin.
        rule.runOnIdle { vm.startTrainer("ruy-lopez", PieceColor.BLACK); vm.studyTrainer(); vm.jumpTrainer(5) }
        assertTrue(vm.uiState.value.trainer!!.branchOffers.any { it.pathId == "ruy-berlin" })
    }

    @Test fun coldRestoreUsesOldPolicyRevisionAfterEditingNewChoices() = fixture { vm, store, _ ->
        rule.runOnIdle { vm.openRepertoire("ruy-lopez", PieceColor.WHITE, "ruy-main", 5) }
        rule.waitUntil(20_000) { vm.uiState.value.repertoireEditor != null }
        val initial = vm.uiState.value.repertoireEditor!!.policy
        rule.runOnIdle { vm.practiceRepertoire(initial) }
        rule.waitUntil(20_000) { vm.uiState.value.trainer?.repertoirePolicy != null }
        rule.runOnIdle { vm.studyTrainer(); vm.jumpTrainer(6) }
        rule.waitUntil(20_000) { vm.uiState.value.persistenceStatus == "Saved for offline resume" }
        val before = vm.uiState.value.trainer!!
        rule.runOnIdle { vm.moveRepertoireCursor("ruy-berlin", 6) }
        rule.waitUntil(20_000) { vm.uiState.value.repertoireEditor?.pathId == "ruy-berlin" }
        rule.runOnIdle { vm.adoptRepertoireRoute() }
        revision(vm, 1)
        val owner = ViewModelStore()
        lateinit var restored: AppViewModel
        try {
            rule.runOnIdle { restored = AppViewModel(SavedStateHandle(), learningStore = store); owner.put("cold", restored) }
            rule.waitUntil(20_000) { restored.uiState.value.trainer != null }
            val after = restored.uiState.value.trainer!!
            assertEquals(before.position, after.position); assertEquals(6, after.ply)
            assertEquals(0, after.repertoirePolicy!!.revision)
            assertEquals(setOf("ruy-main"), after.replay.graph.paths.keys)
            assertEquals(1, runBlocking { store.repertoirePolicy(initial.id) }!!.revision)
        } finally { rule.runOnIdle { owner.clear() } }
    }

    @Test fun sourcedRuyPolicyRetainsAllExplorerRoutesAndShowsFiniteScope() = fixture { vm, store, _ ->
        runBlocking { store.install(BundledContent.read(rule.activity.assets, BundledContent.choices.first())) }
        rule.waitUntil(60_000) { vm.uiState.value.sourcedOpenings.size == 149 }
        val ruy = vm.uiState.value.sourcedOpenings.single { it.name == "Ruy Lopez" }
        val route = ruy.variations.maxBy { it.steps.size }
        for (side in PieceColor.entries) {
            rule.runOnIdle { vm.openRepertoire(ruy.id, side, route.id) }
            rule.waitUntil(60_000) { vm.uiState.value.repertoireEditor?.policy?.side == side && !vm.uiState.value.repertoireLoading }
            val editor = vm.uiState.value.repertoireEditor!!
            assertEquals(235, editor.book.graph.paths.size)
            assertTrue(route.id in editor.coverage.eligiblePathIds)
            assertTrue(editor.coverage.sourceBoundaries > 0)
            rule.runOnIdle { vm.practiceRepertoire(editor.policy, route.id) }
            rule.waitUntil(60_000) { vm.uiState.value.trainer?.repertoirePolicy?.side == side && !vm.uiState.value.lessonLoading }
            assertEquals(route.id, vm.uiState.value.trainer!!.replay.pathId)
            assertEquals(side, vm.uiState.value.trainer!!.playerSide)
        }
        assertEquals(235, vm.uiState.value.sourcedOpenings.single { it.id == ruy.id }.variations.size)
        assertEquals(2, runBlocking { store.repertoirePolicies.first() }.size)
    }
}
