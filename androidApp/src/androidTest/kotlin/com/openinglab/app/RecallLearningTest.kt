// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.analysis.AndroidStockfish
import com.openinglab.app.ui.*
import com.openinglab.app.ui.theme.OpeningLabTheme
import com.openinglab.shared.analysis.*
import com.openinglab.app.analysis.ProcessUciTransport
import com.openinglab.shared.data.OpeningCatalog
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.repertoire.*
import com.openinglab.shared.review.*
import com.openinglab.shared.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Unique synthetic databases, on the isolated serial only; never instrument the owner install. */
@RunWith(AndroidJUnit4::class)
class RecallLearningTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun click(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
    private fun waitSaved(vm: AppViewModel) = rule.waitUntil(30_000) {
        vm.recall.state.value.pendingWrites == 0 && vm.uiState.value.persistenceStatus == "Saved for offline resume"
    }
    private fun fixture(test: (RoomLearningStore, AppViewModel, ViewModelStore, String) -> Unit) {
        val name = "recall-test-${UUID.randomUUID()}.db"
        val context = rule.activity.applicationContext
        val db = createAndroidLearningDatabase(context, name); val store = RoomLearningStore(db); val owner = ViewModelStore()
        lateinit var vm: AppViewModel
        try {
            rule.runOnIdle { vm = AppViewModel(SavedStateHandle(), learningStore = store); owner.put("recall", vm)
                rule.activity.setContent { OpeningLabTheme { OpeningLabApp(vm) } } }
            rule.waitUntil(10_000) { !vm.recall.state.value.loading }
            test(store, vm, owner, name)
        } finally { clearTestViewModels(owner) { rule.runOnIdle(it) }; db.close(); context.deleteDatabase(name) }
    }

    @Test fun realReviewAndProfileDistinguishUnaidedAutomaticHintsAndStudyWithoutFakeCounts() = fixture { _, vm, _, _ ->
        rule.runOnIdle { vm.selectTab(MainTab.REVIEW) }
        rule.onNodeWithTag("recall-empty").assertExists()
        rule.runOnIdle { vm.startTrainer("ruy-lopez", PieceColor.WHITE) }; waitSaved(vm)
        rule.runOnIdle { vm.trainerTap("e2"); vm.trainerTap("e4") }
        rule.waitUntil(10_000) { vm.uiState.value.trainer?.ply == 2 }; waitSaved(vm)
        rule.waitUntil(10_000) { vm.recall.state.value.totals.unaided == 1 }
        assertEquals(1, vm.recall.state.value.totals.unaided)
        rule.runOnIdle { vm.trainerTap("b1"); vm.trainerTap("c3") }
        assertEquals(setOf("g1", "f3"), vm.uiState.value.trainer!!.hintSquares)
        rule.runOnIdle { vm.trainerTap("g1"); vm.trainerTap("f3") }
        rule.waitUntil(10_000) { vm.uiState.value.trainer?.ply == 4 }; waitSaved(vm)
        rule.waitUntil(10_000) { vm.recall.state.value.totals.assisted == 1 }
        assertEquals(1, vm.recall.state.value.totals.notRecalled)
        assertEquals(0, vm.recall.state.value.totals.legacyUngraded)
        rule.runOnIdle { vm.studyTrainer() }; waitSaved(vm)
        rule.waitUntil(10_000) { vm.recall.state.value.totals.studyViews == 1 }
        val before = vm.recall.state.value.totals.attempts
        rule.runOnIdle { vm.jumpTrainer(8) }; waitSaved(vm)
        assertEquals(before, vm.recall.state.value.totals.attempts)
        rule.runOnIdle { vm.practiceTrainer(); vm.trainerTap("e2"); vm.trainerTap("e4") }
        rule.waitUntil(10_000) { vm.uiState.value.trainer?.ply == 2 }; waitSaved(vm)
        rule.waitUntil(10_000) { vm.recall.state.value.totals.assisted == 2 }
        assertEquals(0, vm.recall.state.value.scopes.single().established)
        rule.onNodeWithTag("recall-due-count").performScrollTo().assertTextContains("chosen decisions", substring = true)
        rule.runOnIdle { vm.selectTab(MainTab.PROFILE) }
        rule.onNodeWithText("Assisted correct answers").performScrollTo().assertExists()
        rule.onNodeWithText("82%").assertDoesNotExist()
        rule.onNodeWithText("4 of 5 study days complete").assertDoesNotExist()
    }

    @Test fun onePositionQueueWaitsForActualWriteAndColdResumeKeepsAssistanceAndExactScope() = fixture { store, vm, owner, name ->
        rule.runOnIdle { vm.startTrainer("ruy-lopez", PieceColor.BLACK) }
        rule.waitUntil(10_000) { vm.uiState.value.trainer?.ply == 1 }; waitSaved(vm)
        // Saved acknowledges the transaction; the Room aggregate Flow is independently asynchronous.
        rule.waitUntil(10_000) { vm.recall.state.value.scopes.size == 1 }
        val summary = vm.recall.state.value.scopes.single()
        assertEquals(PieceColor.BLACK, runBlocking { store.recallCards(summary.scope.id, System.currentTimeMillis()) }.first().target.side)
        rule.runOnIdle { vm.selectTab(MainTab.REVIEW) }
        click("begin-recall-review")
        rule.waitUntil(10_000) { vm.uiState.value.trainer?.reviewTargetId != null && !vm.uiState.value.lessonLoading }
        val cardId = vm.uiState.value.trainer!!.reviewTargetId!!
        val card = runBlocking { store.recallCard(summary.scope.id, cardId) }!!
        assertEquals(card.target.prefix, vm.uiState.value.trainer!!.replay.moves.take(card.target.ply).map { it.move.uci })
        rule.onNodeWithTag("review-card-context").assertExists()
        rule.onNodeWithTag("next-review-card").assertIsNotEnabled()
        rule.onNodeWithTag("flip-side").assertIsNotEnabled()
        rule.runOnIdle { vm.showHint() }; waitSaved(vm)
        val coldOwner = ViewModelStore()
        lateinit var cold: AppViewModel
        clearTestViewModels(owner) { rule.runOnIdle(it) }
        rule.runOnIdle { cold = AppViewModel(SavedStateHandle(), learningStore = store); coldOwner.put("cold", cold) }
        try {
            rule.waitUntil(10_000) { cold.uiState.value.trainer?.reviewTargetId == cardId }
            assertEquals(summary.scope.id, cold.uiState.value.trainer!!.reviewScopeId)
            assertTrue(cold.uiState.value.trainer!!.currentAssisted)
            assertTrue(RecallHelp.HINT in cold.uiState.value.trainer!!.recallHelp)
            rule.runOnIdle { cold.trainerTap(card.target.expectedUci.take(2)); cold.trainerTap(card.target.expectedUci.substring(2, 4)) }
            rule.waitUntil(10_000) { cold.recall.state.value.pendingWrites == 0 }
            rule.waitForIdle()
            assertTrue("${cold.recall.state.value.error}; ${cold.uiState.value.persistenceStatus}; ${cold.uiState.value.trainer?.feedback}", cold.uiState.value.trainer?.reviewSaved == true)
            waitSaved(cold)
            assertTrue(cold.uiState.value.trainer!!.reviewAnswered)
            val attempted = cold.uiState.value.trainer!!.reviewAttemptId!!
            assertTrue(runBlocking { store.hasRecallEvent(attempted, cardId) })
            rule.runOnIdle { cold.nextRecallReview() }
            rule.waitUntil(10_000) { cold.uiState.value.trainer?.reviewTargetId != null && cold.uiState.value.trainer?.reviewTargetId != cardId && !cold.uiState.value.lessonLoading }
            assertEquals(1, cold.recall.state.value.totals.assisted)
            assertEquals(0, cold.recall.state.value.totals.unaided)
        } finally { clearTestViewModels(coldOwner) { rule.runOnIdle(it) } }
    }

    @Test fun namedSetReviewDenominatorIncludesEveryPinnedMemberAndRetainsOlderPolicies() = fixture { store, vm, _, _ ->
        val books = listOf("ruy-lopez", "sicilian").map { RepertoireBook(LessonGraph.fromOpening(OpeningCatalog.byId(it))) }
        val policies = books.map { it.seed(PieceColor.WHITE, it.graph.originalPathId) }
        val set = RepertoireSet("synthetic-recall-set", "White e4 decisions", PieceColor.WHITE, policies.map { RepertoirePolicyRef(it.id, 0) })
        runBlocking { policies.forEach { store.saveRepertoirePolicy(it) }; store.saveRepertoireSet(set) }
        rule.runOnIdle { vm.checkRepertoireSet(set) }
        rule.waitUntil(20_000) { vm.uiState.value.checkedSet?.ready == true }
        rule.runOnIdle { vm.practiceRepertoireSet(vm.uiState.value.checkedSet!!) }; waitSaved(vm)
        rule.waitUntil(10_000) { vm.recall.state.value.scopes.any { it.scope.setId == set.id } }
        val summary = vm.recall.state.value.scopes.single { it.scope.setId == set.id }
        assertEquals(books.zip(policies).sumOf { (b, p) -> RecallPlanner.policy(b, p).entries.size }, summary.total)
        assertEquals(0, summary.established)
        runBlocking { store.saveRepertoirePolicy(books.first().adoptRoute(policies.first(), "ruy-berlin")) }
        rule.runOnIdle { vm.moveSetPractice(1) }; waitSaved(vm)
        assertEquals(summary.scope.id, vm.recall.state.value.scopes.single { it.scope.setId == set.id }.scope.id)
        assertEquals(0, vm.uiState.value.trainer!!.repertoirePolicy!!.revision)
    }

    @Test fun failedGradeRetryKeepsTheSameEventAndUnlocksNextOnlyAfterDurableSave() {
        val context = rule.activity.applicationContext; val name = "recall-retry-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name); val store = RoomLearningStore(db)
        val owner = ViewModelStore(); val seen = mutableListOf<String>(); var fail = true
        val failing = object : LearningStore by store {
            override suspend fun recordRecall(event: RecallEvent) {
                seen += event.attempt.id
                if (fail) { fail = false; throw IllegalStateException("Synthetic transient save failure") }
                store.recordRecall(event)
            }
        }
        lateinit var vm: AppViewModel
        try {
            rule.runOnIdle { vm = AppViewModel(SavedStateHandle(), learningStore = failing); owner.put("retry", vm)
                rule.activity.setContent { OpeningLabTheme { OpeningLabApp(vm) } }; vm.startTrainer("ruy-lopez", PieceColor.WHITE) }
            waitSaved(vm)
            rule.waitUntil(10_000) { vm.recall.state.value.scopes.size == 1 }
            rule.runOnIdle { vm.selectTab(MainTab.REVIEW) }; click("begin-recall-review")
            rule.waitUntil(10_000) { vm.uiState.value.trainer?.reviewTargetId != null && !vm.uiState.value.lessonLoading }
            val t = vm.uiState.value.trainer!!; val id = t.reviewTargetId!!
            val card = runBlocking { store.recallCard(t.reviewScopeId!!, id) }!!
            rule.runOnIdle { vm.trainerTap(card.target.expectedUci.take(2)); vm.trainerTap(card.target.expectedUci.substring(2, 4)) }
            rule.waitUntil(10_000) { vm.recall.state.value.retryableWrites == 1 && vm.recall.state.value.pendingWrites == 0 }
            rule.onNodeWithTag("next-review-card").assertIsNotEnabled()
            assertEquals(0, vm.recall.state.value.totals.unaided)
            click("trainer-retry-recall")
            rule.waitUntil(10_000) { vm.uiState.value.trainer?.reviewSaved == true }; waitSaved(vm)
            rule.waitUntil(10_000) { vm.recall.state.value.totals.unaided == 1 }
            rule.onNodeWithTag("next-review-card").assertIsEnabled()
            assertEquals(2, seen.size); assertEquals(seen[0], seen[1])
            assertTrue(runBlocking { store.hasRecallEvent(seen[0], id) })
            rule.runOnIdle { vm.recall.retryFailedWrites() }; rule.waitForIdle()
            assertEquals(1, vm.recall.state.value.totals.attempts)
        } finally { clearTestViewModels(owner) { rule.runOnIdle(it) }; db.close(); context.deleteDatabase(name) }
    }

    @Test fun successfulActualEngineExposureColdPersistsAsAssistanceButFailureAndLateStopDoNot() {
        val context = rule.activity.applicationContext; val name = "recall-engine-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name); val store = RoomLearningStore(db)
        val owner = ViewModelStore(); val coldOwner = ViewModelStore()
        var mode = "actual"; var successful: EngineAnalysis? = null
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val engine = object : ChessAnalysisEngine {
            override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget): EngineAnalysis = when (mode) {
                "actual" -> AndroidStockfish.engine(context).analyze(position, originalMove, budget).also { successful = it }
                "late" -> withContext(NonCancellable) { started.complete(Unit); release.await(); requireNotNull(successful) }
                else -> throw EngineProtocolException("Synthetic unavailable")
            }
        }
        lateinit var vm: AppViewModel; lateinit var cold: AppViewModel
        try {
            rule.runOnIdle { vm = AppViewModel(SavedStateHandle(), learningStore = store, analysisEngine = engine); owner.put("engine", vm)
                vm.startTrainer("ruy-lopez", PieceColor.WHITE); vm.analyzeTrainer() }
            rule.waitUntil(60_000) { vm.uiState.value.engineAnalysis is EngineAnalysisUiState.Ready || vm.uiState.value.engineAnalysis is EngineAnalysisUiState.Error }
            (vm.uiState.value.engineAnalysis as? EngineAnalysisUiState.Error)?.let { error ->
                assertTrue(error.message, error.message.contains("timed out"))
                assertFalse(vm.uiState.value.trainer!!.currentAssisted)
                rule.waitUntil(10_000) { ProcessUciTransport.activeProcesses.get() == 0 }
                rule.runOnIdle { vm.analyzeTrainer() }
                rule.waitUntil(60_000) { vm.uiState.value.engineAnalysis is EngineAnalysisUiState.Ready || vm.uiState.value.engineAnalysis is EngineAnalysisUiState.Error }
            }
            assertTrue(vm.uiState.value.engineAnalysis.toString(), vm.uiState.value.engineAnalysis is EngineAnalysisUiState.Ready)
            assertTrue(vm.uiState.value.trainer!!.currentAssisted)
            assertTrue(RecallHelp.ENGINE in vm.uiState.value.trainer!!.recallHelp); waitSaved(vm)
            clearTestViewModels(owner) { rule.runOnIdle(it) }
            rule.runOnIdle { cold = AppViewModel(SavedStateHandle(), learningStore = store, analysisEngine = engine); coldOwner.put("cold", cold) }
            rule.waitUntil(10_000) { cold.uiState.value.trainer != null }
            assertTrue(RecallHelp.ENGINE in cold.uiState.value.trainer!!.recallHelp)
            assertEquals(EngineAnalysisUiState.Idle, cold.uiState.value.engineAnalysis)
            rule.runOnIdle { cold.trainerTap("e2"); cold.trainerTap("e4") }
            rule.waitUntil(10_000) { cold.uiState.value.trainer?.ply == 2 }; waitSaved(cold)
            rule.waitUntil(10_000) { cold.recall.state.value.totals.assisted == 1 }
            mode = "fail"
            rule.runOnIdle { cold.restartTrainer(); cold.analyzeTrainer() }
            rule.waitUntil(10_000) { cold.uiState.value.engineAnalysis is EngineAnalysisUiState.Error }
            assertFalse(cold.uiState.value.trainer!!.currentAssisted)
            rule.runOnIdle { cold.trainerTap("e2"); cold.trainerTap("e4") }
            rule.waitUntil(10_000) { cold.uiState.value.trainer?.ply == 2 }; waitSaved(cold)
            mode = "late"
            rule.runOnIdle { cold.restartTrainer(); cold.analyzeTrainer() }
            rule.waitUntil(10_000) { started.isCompleted }
            rule.runOnIdle { cold.stopEngineAnalysis(); release.complete(Unit) }
            rule.waitForIdle()
            assertEquals(EngineAnalysisUiState.Idle, cold.uiState.value.engineAnalysis)
            assertFalse(cold.uiState.value.trainer!!.currentAssisted)
            rule.runOnIdle { cold.trainerTap("e2"); cold.trainerTap("e4") }
            rule.waitUntil(10_000) { cold.uiState.value.trainer?.ply == 2 }; waitSaved(cold)
            rule.waitUntil(10_000) { cold.recall.state.value.totals.unaided == 2 }
            assertEquals(1, cold.recall.state.value.totals.assisted)
        } finally { release.complete(Unit); clearTestViewModels(owner, coldOwner) { rule.runOnIdle(it) }; db.close(); context.deleteDatabase(name) }
    }
}
