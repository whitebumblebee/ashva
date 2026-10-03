// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.analysis.AndroidStockfish
import com.openinglab.app.analysis.ProcessUciTransport
import com.openinglab.app.ui.*
import com.openinglab.shared.analysis.*
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.model.PieceColor
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineAnalysisTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun actualOfflineEngineShowsCandidatesAndSeparatePreviewWithoutChangingLesson() {
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        rule.waitUntil(30_000) { vm.uiState.value.persistenceStatus != "Loading saved lesson…" }
        rule.onNodeWithText("Explore").performClick()
        rule.onNodeWithText("Starter").performClick()
        rule.onNodeWithText("Ruy López").performScrollTo().performClick()
        rule.onNodeWithText("Play White").performScrollTo().performClick()
        rule.runOnIdle { vm.studyTrainer(); vm.jumpTrainer(5) }
        val before = vm.uiState.value.trainer!!.replay.snapshot()
        rule.onNodeWithTag("engine-analyze").performScrollTo().performClick()
        rule.waitUntil(60_000) { vm.uiState.value.engineAnalysis is EngineAnalysisUiState.Ready || vm.uiState.value.engineAnalysis is EngineAnalysisUiState.Error }
        assertTrue(vm.uiState.value.engineAnalysis.toString(), vm.uiState.value.engineAnalysis is EngineAnalysisUiState.Ready)
        val ready = vm.uiState.value.engineAnalysis as EngineAnalysisUiState.Ready
        assertEquals("Stockfish", ready.result.engine.name); assertEquals("19", ready.result.engine.version)
        assertNotNull(ready.result.original); assertTrue(ready.result.alternatives!!.lines.isNotEmpty())
        assertEquals(ready.lines.size, ready.previewExplanations.size)
        ready.lines.forEachIndexed { index, line -> assertEquals(line.san, ready.previewExplanations[index].map { it.san }) }
        rule.onNodeWithTag("engine-idea-0").performScrollTo().assertTextContains("BOARD FACT", substring = true)
        rule.onNodeWithTag("engine-budget").performScrollTo().assertTextContains("WHITE score perspective", substring = true)
        rule.onNodeWithTag("engine-provenance").performScrollTo().assertTextContains(ready.result.engine.binarySha256, substring = true)
        rule.onNodeWithTag("engine-explore-0").performScrollTo().performClick()
        rule.onNodeWithTag("engine-preview").performScrollTo().assertExists()
        rule.onNodeWithTag("engine-preview-idea").performScrollTo().assertTextContains(ready.lines.first().san.first(), substring = true)
        rule.onNodeWithTag("engine-last").performScrollTo().performClick()
        assertEquals(ready.previewPositions.first().lastIndex, (vm.uiState.value.engineAnalysis as EngineAnalysisUiState.Ready).previewPly)
        assertEquals(before, vm.uiState.value.trainer!!.replay.snapshot())
        rule.onNodeWithTag("engine-return").performScrollTo().performClick()
        assertNull((vm.uiState.value.engineAnalysis as EngineAnalysisUiState.Ready).previewIndex)
        assertEquals(before, vm.uiState.value.trainer!!.replay.snapshot())
        assertEquals(0, ProcessUciTransport.activeProcesses.get())
    }

    @Test fun actualEngineReportsBlackMateAndCancelsItsNativeProcess() = runBlocking {
        val engine = AndroidStockfish.engine(rule.activity)
        val position = AnalysisPosition(BoardPosition.START_FEN, listOf("g2g4", "e7e5", "f2f3"))
        val result = withContext(Dispatchers.Default) { engine.analyze(position, "d8h4", AnalysisBudget(multiPv = 1)) }
        assertEquals(PieceColor.BLACK, result.rootSide)
        val line = result.alternatives!!.lines.single()
        assertEquals(listOf("Qh4#"), line.san)
        assertEquals(EngineScore.Mate(1), line.score)
        assertEquals(EngineScore.Mate(-1), line.score.forSide(PieceColor.BLACK, PieceColor.WHITE))
        assertEquals(0, ProcessUciTransport.activeProcesses.get())
        val job = launch(Dispatchers.Default) { engine.analyze(AnalysisPosition(BoardPosition.START_FEN), budget = AnalysisBudget(nodes = 200_000, moveTimeMillis = 3_000)) }
        withTimeout(15_000) { while (ProcessUciTransport.activeProcesses.get() == 0) delay(10) }
        job.cancelAndJoin()
        assertEquals(0, ProcessUciTransport.activeProcesses.get())
    }

    @Test fun positionChangesBackgroundAndExplicitStopCancelWithoutPublishingStaleResults() {
        val owner = ViewModelStore()
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val fake = object : ChessAnalysisEngine {
            override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget): EngineAnalysis {
                started.complete(Unit)
                try { awaitCancellation() } finally { stopped.complete(Unit) }
            }
        }
        val vm = AppViewModel(SavedStateHandle(), analysisEngine = fake)
        owner.put("engine", vm)
        try {
            rule.runOnIdle { vm.startTrainer("ruy-lopez", PieceColor.WHITE); vm.studyTrainer(); vm.analyzeTrainer() }
            rule.waitUntil(10_000) { started.isCompleted }
            rule.runOnIdle { vm.jumpTrainer(1) }
            rule.waitUntil(10_000) { stopped.isCompleted }
            assertEquals(EngineAnalysisUiState.Idle, vm.uiState.value.engineAnalysis)
            rule.runOnIdle { vm.analyzeTrainer(); vm.pauseTrainer() }
            assertEquals(EngineAnalysisUiState.Idle, vm.uiState.value.engineAnalysis)
            rule.runOnIdle { vm.analyzeTrainer(); vm.stopEngineAnalysis() }
            assertEquals(EngineAnalysisUiState.Idle, vm.uiState.value.engineAnalysis)
        } finally { rule.runOnIdle { owner.clear() } }
    }

    @Test fun unavailableAndTimedOutEnginesAreErrorsNotPerpetualLoading() {
        val owner = ViewModelStore()
        val unavailable = AppViewModel(SavedStateHandle())
        val timedOut = AppViewModel(SavedStateHandle(), analysisEngine = object : ChessAnalysisEngine {
            override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget): EngineAnalysis =
                withTimeout(1) { awaitCancellation() }
        })
        owner.put("missing", unavailable); owner.put("timeout", timedOut)
        try {
            rule.runOnIdle {
                unavailable.startTrainer("ruy-lopez", PieceColor.WHITE); unavailable.analyzeTrainer()
                timedOut.startTrainer("ruy-lopez", PieceColor.WHITE); timedOut.analyzeTrainer()
            }
            rule.waitUntil(10_000) { timedOut.uiState.value.engineAnalysis is EngineAnalysisUiState.Error }
            assertTrue(unavailable.uiState.value.engineAnalysis is EngineAnalysisUiState.Error)
            assertTrue((timedOut.uiState.value.engineAnalysis as EngineAnalysisUiState.Error).message.contains("timed out"))
        } finally { rule.runOnIdle { owner.clear() } }
    }
}
