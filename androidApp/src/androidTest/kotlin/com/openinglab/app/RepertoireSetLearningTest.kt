// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.ui.*
import com.openinglab.app.ui.theme.OpeningLabTheme
import com.openinglab.shared.data.OpeningCatalog
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.repertoire.*
import com.openinglab.shared.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Exact-serial isolated emulator only; every learner fixture has its own unique database. */
@RunWith(AndroidJUnit4::class)
class RepertoireSetLearningTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun book(id: String) = RepertoireBook(LessonGraph.fromOpening(OpeningCatalog.byId(id)))
    private fun policy(id: String, side: PieceColor = PieceColor.WHITE) = book(id).let { it.seed(side, it.graph.originalPathId) }
    private fun click(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()

    @Test fun composeMembershipCreatesPinnedSetAndPracticesAcrossFamilies() {
        val context = rule.activity.applicationContext
        val name = "set-ui-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name); val store = RoomLearningStore(db); val owner = ViewModelStore()
        val ruy = policy("ruy-lopez"); val sicilian = policy("sicilian"); val black = policy("sicilian", PieceColor.BLACK)
        runBlocking { listOf(ruy, sicilian, black).forEach { store.saveRepertoirePolicy(it) } }
        lateinit var vm: AppViewModel
        try {
            rule.runOnIdle {
                vm = AppViewModel(SavedStateHandle(), learningStore = store); owner.put("sets", vm)
                rule.activity.setContent { OpeningLabTheme { OpeningLabApp(vm) } }
            }
            rule.waitUntil(20_000) { vm.uiState.value.repertoirePolicies.size == 3 }
            click("my-repertoires"); click("create-repertoire-set")
            rule.onNodeWithTag("set-name").performTextInput("White e4 study")
            rule.onNodeWithTag("set-member-${black.id}").assertDoesNotExist()
            click("set-member-${ruy.id}"); click("set-member-${sicilian.id}")
            rule.onNodeWithTag("save-repertoire-set").performClick()
            rule.waitUntil(20_000) { vm.uiState.value.repertoireSets.size == 1 && !vm.uiState.value.setLoading }
            val set = vm.uiState.value.repertoireSets.single()
            assertEquals("White e4 study", set.name); assertEquals(PieceColor.WHITE, set.side)
            assertEquals(setOf(ruy.id, sicilian.id), set.members.map { it.id }.toSet())
            rule.onNodeWithTag("my-repertoires-scroll").performScrollToKey("set:${set.id}")
            click("check-set-${set.id}")
            rule.waitUntil(20_000) { vm.uiState.value.checkedSet != null }
            val plan = vm.uiState.value.checkedSet!!; assertTrue(plan.ready)
            click("practice-set-${set.id}")
            rule.waitUntil(20_000) { vm.uiState.value.trainer?.repertoireSetSession?.index == 0 }
            rule.onNodeWithTag("set-practice-position").performScrollTo().assertTextContains("White e4 study", substring = true)
            rule.onNodeWithTag("flip-side").assertIsNotEnabled()
            click("set-next")
            rule.waitUntil(20_000) { vm.uiState.value.trainer?.repertoireSetSession?.index == 1 }
            assertEquals(plan.items[1].lessonId, vm.uiState.value.trainer!!.opening.id)
            click("set-previous")
            rule.waitUntil(20_000) { vm.uiState.value.trainer?.repertoireSetSession?.index == 0 }
            runBlocking { assertEquals(ruy, store.repertoirePolicy(ruy.id)); assertEquals(sicilian, store.repertoirePolicy(sicilian.id)) }
        } finally { clearTestViewModels(owner) { rule.runOnIdle(it) }; db.close(); context.deleteDatabase(name) }
    }

    @Test fun incompatiblePreferencesRemainSavedAndUnifiedPracticeIsDisabled() {
        val context = rule.activity.applicationContext
        val name = "set-conflict-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name); val store = RoomLearningStore(db); val owner = ViewModelStore()
        val ruy = policy("ruy-lopez"); val london = policy("london")
        val set = RepertoireSet("test-conflict-set", "Separate first moves", PieceColor.WHITE,
            listOf(RepertoirePolicyRef(ruy.id, 0), RepertoirePolicyRef(london.id, 0)))
        runBlocking { store.saveRepertoirePolicy(ruy); store.saveRepertoirePolicy(london); store.saveRepertoireSet(set) }
        lateinit var vm: AppViewModel
        try {
            rule.runOnIdle {
                vm = AppViewModel(SavedStateHandle(), learningStore = store); owner.put("sets", vm)
                rule.activity.setContent { OpeningLabTheme { OpeningLabApp(vm) } }
            }
            rule.waitUntil(20_000) { vm.uiState.value.repertoireSets.size == 1 }
            click("my-repertoires")
            rule.onNodeWithTag("my-repertoires-scroll").performScrollToKey("set:${set.id}"); click("check-set-${set.id}")
            rule.waitUntil(20_000) { vm.uiState.value.checkedSet != null }
            val plan = vm.uiState.value.checkedSet!!; assertFalse(plan.ready)
            assertTrue(plan.overview.conflicts.isNotEmpty())
            rule.onNodeWithTag("practice-set-${set.id}").performScrollTo().assertIsNotEnabled()
            runBlocking { assertEquals(set, store.repertoireSet(set.id)); assertEquals(ruy, store.repertoirePolicy(ruy.id)); assertEquals(london, store.repertoirePolicy(london.id)) }
        } finally { clearTestViewModels(owner) { rule.runOnIdle(it) }; db.close(); context.deleteDatabase(name) }
    }

    @Test fun coldResumeKeepsOldSetMemberRevisionsQueueIndexColorAndCursor() {
        val context = rule.activity.applicationContext
        val name = "set-cold-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name); val store = RoomLearningStore(db)
        val ruy = policy("ruy-lopez"); val sicilian = policy("sicilian")
        val set = RepertoireSet("test-cold-set", "White e4", PieceColor.WHITE,
            listOf(RepertoirePolicyRef(ruy.id, 0), RepertoirePolicyRef(sicilian.id, 0)))
        runBlocking { store.saveRepertoirePolicy(ruy); store.saveRepertoirePolicy(sicilian); store.saveRepertoireSet(set) }
        val firstOwner = ViewModelStore(); val secondOwner = ViewModelStore(); lateinit var first: AppViewModel
        rule.runOnIdle { first = AppViewModel(SavedStateHandle(), learningStore = store); firstOwner.put("sets", first); first.checkRepertoireSet(set) }
        rule.waitUntil(20_000) { first.uiState.value.checkedSet?.ready == true }
        rule.runOnIdle { first.practiceRepertoireSet(first.uiState.value.checkedSet!!) }
        rule.waitUntil(20_000) { first.uiState.value.trainer?.repertoireSetSession != null }
        rule.runOnIdle { first.moveSetPractice(1) }
        rule.waitUntil(20_000) { first.uiState.value.trainer?.repertoireSetSession?.index == 1 }
        rule.runOnIdle { first.studyTrainer(); first.jumpTrainer(3) }
        rule.waitUntil(20_000) { first.uiState.value.persistenceStatus == "Saved for offline resume" }
        val before = first.uiState.value.trainer!!
        runBlocking {
            store.saveRepertoirePolicy(book("ruy-lopez").adoptRoute(ruy, "ruy-berlin"))
            store.saveRepertoireSet(set.copy(revision = 1, members = listOf(RepertoirePolicyRef(sicilian.id, 0), RepertoirePolicyRef(ruy.id, 1))))
        }
        clearTestViewModels(firstOwner) { rule.runOnIdle(it) }; db.close()
        val reopened = createAndroidLearningDatabase(context, name)
        try {
            lateinit var restored: AppViewModel
            rule.runOnIdle { restored = AppViewModel(SavedStateHandle(), learningStore = RoomLearningStore(reopened)); secondOwner.put("sets", restored) }
            rule.waitUntil(20_000) { restored.uiState.value.trainer != null }
            val after = restored.uiState.value.trainer!!
            assertEquals(before.position, after.position); assertEquals(before.replay.snapshot(), after.replay.snapshot())
            assertEquals(PieceColor.WHITE, after.playerSide); assertEquals(LessonMode.STUDY, after.mode)
            val session = requireNotNull(after.repertoireSetSession)
            assertEquals(0, session.plan.set.revision); assertEquals(1, session.index)
            assertEquals(set.members, session.plan.set.members)
            rule.runOnIdle { restored.moveSetPractice(-1) }
            rule.waitUntil(20_000) { restored.uiState.value.trainer?.repertoireSetSession?.index == 0 }
            assertEquals(0, restored.uiState.value.trainer!!.repertoirePolicy!!.revision)
        } finally { clearTestViewModels(secondOwner) { rule.runOnIdle(it) }; reopened.close(); context.deleteDatabase(name) }
    }
}
