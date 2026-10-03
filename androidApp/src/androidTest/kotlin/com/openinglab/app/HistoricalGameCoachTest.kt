// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.analysis.ProcessUciTransport
import com.openinglab.app.ui.*
import com.openinglab.shared.analysis.*
import com.openinglab.shared.chess.*
import com.openinglab.shared.games.LibraryScore
import com.openinglab.shared.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoricalGameCoachTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]
    private fun click(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
    private fun study(model: AppViewModel = vm) = requireNotNull(model.gameLibrary.state.value.study)
    private fun teaching(model: AppViewModel = vm): OriginalMoveTeachingUiState.Ready {
        rule.waitUntil(10_000) { model.gameLibrary.state.value.moveTeaching is OriginalMoveTeachingUiState.Ready }
        return model.gameLibrary.state.value.moveTeaching as OriginalMoveTeachingUiState.Ready
    }
    private fun startFixture(model: AppViewModel, text: String = "1. e4 e5 2. Nf3 Nc6 *") {
        rule.runOnIdle { model.gameLibrary.importPgn(text) }
        rule.waitUntil(30_000) { !model.gameLibrary.state.value.importing && model.gameLibrary.state.value.library is GameLibraryUiState.Ready }
        val score = (model.gameLibrary.state.value.library as GameLibraryUiState.Ready).library.scores.filterIsInstance<LibraryScore.Private>().first()
        rule.runOnIdle { model.gameLibrary.start(score.id, PieceColor.BLACK) }
        rule.waitUntil(30_000) { model.gameLibrary.state.value.study != null && !model.gameLibrary.state.value.studyLoading }
    }

    @Test fun actualOfflineOriginalCoachCacheBothPovPreviewReturnAndBackground() {
        val name = "Synthetic coach ${UUID.randomUUID()}"
        val text = "[White \"$name\"]\n[Black \"Synthetic rival\"]\n\n1. e4 {Unverified intent} e5 2. Nf3 Nc6 3. Bb5 a6 *"
        click("gm-game-library")
        rule.runOnIdle { vm.gameLibrary.search(com.openinglab.shared.games.GameLibraryFilter()); vm.gameLibrary.importPgn(text) }
        rule.waitUntil(30_000) { !vm.gameLibrary.state.value.importing && (vm.gameLibrary.state.value.library as? GameLibraryUiState.Ready)?.library?.scores?.any { it.white.name == name } == true }
        val score = (vm.gameLibrary.state.value.library as GameLibraryUiState.Ready).library.scores.single { it.white.name == name }
        rule.onNodeWithTag("game-library-list").performScrollToKey("score-${score.id}")
        click("game-open-BLACK-${score.id}")
        rule.waitUntil(30_000) { vm.gameLibrary.state.value.study?.score?.id == score.id }
        click("game-next"); teaching()
        rule.onNodeWithTag("game-coach-move").performScrollTo().assertTextContains("WHITE played e4", substring = true)
        assertFalse(teaching().facts.idea.explanation.contains("Unverified intent"))
        click("game-next"); teaching(); click("game-previous")
        assertTrue(teaching().cached)
        rule.onNodeWithTag("game-coach-provenance").performScrollTo().assertTextContains("reused checked local explanation", substring = true)
        click("engine-analyze")
        assertFalse(study().isPlaying)
        val root = study().replay.snapshot()
        rule.waitUntil(60_000) { vm.gameLibrary.state.value.engineAnalysis is EngineAnalysisUiState.Ready || vm.gameLibrary.state.value.engineAnalysis is EngineAnalysisUiState.Error }
        // A bounded search may legitimately time out under full-suite load. Exercise one
        // deliberate UI retry, never relax the search ceiling or accept partial output.
        (vm.gameLibrary.state.value.engineAnalysis as? EngineAnalysisUiState.Error)?.let { error ->
            assertTrue(error.message, error.message.contains("timed out"))
            assertEquals(root, study().replay.snapshot())
            assertEquals(score.canonicalPgn, study().score.canonicalPgn)
            rule.waitUntil(10_000) { ProcessUciTransport.activeProcesses.get() == 0 }
            rule.onNodeWithTag("engine-error").performScrollTo().assertExists()
            click("engine-analyze")
            rule.waitUntil(60_000) { vm.gameLibrary.state.value.engineAnalysis is EngineAnalysisUiState.Ready || vm.gameLibrary.state.value.engineAnalysis is EngineAnalysisUiState.Error }
        }
        assertTrue(vm.gameLibrary.state.value.engineAnalysis.toString(), vm.gameLibrary.state.value.engineAnalysis is EngineAnalysisUiState.Ready)
        val ready = vm.gameLibrary.state.value.engineAnalysis as EngineAnalysisUiState.Ready
        assertEquals("Stockfish", ready.result.engine.name)
        assertEquals(study().replay.nextMove!!.move.uci, ready.result.original!!.lines.single().uci.first())
        assertEquals(ready.lines.map { it.san }, ready.previewExplanations.map { ideas -> ideas.map { it.san } })
        rule.onNodeWithTag("engine-budget").performScrollTo().assertTextContains("BLACK score perspective", substring = true)
        rule.onNodeWithTag("engine-provenance").performScrollTo().assertTextContains(ready.result.engine.binarySha256, substring = true)
        click("engine-explore-0"); click("engine-last")
        rule.onNodeWithTag("engine-preview").performScrollTo().assertTextContains("HYPOTHETICAL", substring = true)
        rule.onNodeWithTag("game-play").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("game-next").assertIsNotEnabled()
        assertEquals(root, study().replay.snapshot())
        assertEquals(score.canonicalPgn, study().score.canonicalPgn)
        click("game-flip")
        assertEquals(PieceColor.WHITE, study().replay.playerSide)
        assertEquals(root.ply, study().replay.ply)
        rule.onNodeWithTag("engine-budget").performScrollTo().assertTextContains("WHITE score perspective", substring = true)
        click("engine-return")
        assertNull((vm.gameLibrary.state.value.engineAnalysis as EngineAnalysisUiState.Ready).previewIndex)
        assertEquals(root.copy(playerSide = PieceColor.WHITE), study().replay.snapshot())
        assertEquals(score.result, study().score.result)
        rule.runOnIdle { vm.gameLibrary.togglePlayback(); vm.gameLibrary.analyze() }
        assertFalse(study().isPlaying)
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.waitUntil(10_000) { ProcessUciTransport.activeProcesses.get() == 0 }
        assertEquals(EngineAnalysisUiState.Idle, vm.gameLibrary.state.value.engineAnalysis)
        assertFalse(study().isPlaying)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        teaching()
        assertEquals(root.ply, study().replay.ply)
        click("game-play")
        rule.waitUntil(15_000) { study().replay.atEnd }
        assertEquals(score.san, study().replay.moves.map { it.san })
        assertFalse(study().isPlaying)
    }

    @Test fun lateCancelledEngineCannotPublishAfterPositionNavigationStopOrOpening() {
        val owner = ViewModelStore()
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val ended = CompletableDeferred<Unit>()
        val engine = object : ChessAnalysisEngine {
            override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget): EngineAnalysis {
                started.complete(Unit)
                return withContext(NonCancellable) { release.await(); checkedFixture(position, originalMove, budget).also { ended.complete(Unit) } }
            }
        }
        lateinit var model: AppViewModel
        rule.runOnIdle { model = AppViewModel(SavedStateHandle(), analysisEngine = engine); owner.put("coach", model) }
        try {
            startFixture(model)
            rule.runOnIdle { model.gameLibrary.analyze() }
            rule.waitUntil(10_000) { started.isCompleted }
            rule.runOnIdle { model.gameLibrary.jump(1) }
            release.complete(Unit)
            rule.waitUntil(10_000) { ended.isCompleted }
            assertEquals(EngineAnalysisUiState.Idle, model.gameLibrary.state.value.engineAnalysis)
            assertEquals(1, study(model).replay.ply)
            rule.runOnIdle { model.gameLibrary.analyze(); model.gameLibrary.leaveReplay() }
            assertEquals(EngineAnalysisUiState.Idle, model.gameLibrary.state.value.engineAnalysis)
            rule.runOnIdle { model.gameLibrary.analyze(); model.gameLibrary.stopAnalysis() }
            assertEquals(EngineAnalysisUiState.Idle, model.gameLibrary.state.value.engineAnalysis)
            rule.runOnIdle { model.gameLibrary.analyze(); model.startTrainer("london", PieceColor.WHITE) }
            assertFalse(model.gameLibrary.state.value.active)
            assertEquals(EngineAnalysisUiState.Idle, model.gameLibrary.state.value.engineAnalysis)
        } finally { release.complete(Unit); rule.runOnIdle { owner.clear() } }
    }

    @Test fun nearEqualAndMalformedUnavailableTimeoutOutputAreNotFakeRecommendations() {
        val owner = ViewModelStore()
        var mode = "valid"
        val engine = object : ChessAnalysisEngine {
            override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget): EngineAnalysis {
                if (mode == "timeout") return withTimeout(1) { awaitCancellation() }
                val valid = checkedFixture(position, originalMove, budget)
                return when (mode) {
                    "wrong-position" -> valid.copy(position = AnalysisPosition(BoardPosition.START_FEN, listOf("d2d4")))
                    "missing-original" -> valid.copy(original = null)
                    "wrong-san" -> valid.copy(alternatives = valid.alternatives!!.copy(lines = valid.alternatives!!.lines.map { it.copy(san = listOf("Qa9")) }))
                    "fake-terminal" -> valid.copy(terminal = PositionStatus.CHECKMATE, alternatives = null, original = null)
                    else -> valid
                }
            }
        }
        lateinit var model: AppViewModel; lateinit var unavailable: AppViewModel
        rule.runOnIdle {
            model = AppViewModel(SavedStateHandle(), analysisEngine = engine); owner.put("coach", model)
            unavailable = AppViewModel(SavedStateHandle()); owner.put("unavailable", unavailable)
        }
        try {
            startFixture(model)
            rule.runOnIdle { model.gameLibrary.analyze() }
            rule.waitUntil(10_000) { model.gameLibrary.state.value.engineAnalysis is EngineAnalysisUiState.Ready }
            val result = (model.gameLibrary.state.value.engineAnalysis as EngineAnalysisUiState.Ready).result
            assertEquals(MoveComparison.NEAR_EQUAL_AT_BUDGET, result.compareOriginal())
            val before = study(model)
            for (bad in listOf("wrong-position", "missing-original", "wrong-san", "fake-terminal", "timeout")) {
                rule.runOnIdle { mode = bad; model.gameLibrary.analyze() }
                rule.waitUntil(10_000) { model.gameLibrary.state.value.engineAnalysis is EngineAnalysisUiState.Error }
                if (bad == "timeout") assertTrue((model.gameLibrary.state.value.engineAnalysis as EngineAnalysisUiState.Error).message.contains("timed out"))
                assertEquals(before, study(model))
            }
            startFixture(unavailable)
            rule.runOnIdle { unavailable.gameLibrary.analyze() }
            assertTrue(unavailable.gameLibrary.state.value.engineAnalysis is EngineAnalysisUiState.Error)
        } finally { rule.runOnIdle { owner.clear() } }
    }

    @Test fun longHistoryKeepsCompleteReplayAndOfflineTeachingWithoutEngineHistoryTruncation() {
        var calls = 0
        val owner = ViewModelStore()
        lateinit var model: AppViewModel
        rule.runOnIdle {
            model = AppViewModel(SavedStateHandle(), analysisEngine = object : ChessAnalysisEngine {
                override suspend fun analyze(position: AnalysisPosition, originalMove: String?, budget: AnalysisBudget): EngineAnalysis { calls++; error("Not permitted for long history") }
            }); owner.put("coach", model)
        }
        try {
            val text = (1..129).joinToString(" ") { "${it * 2 - 1}. Nf3 Nf6 ${it * 2}. Ng1 Ng8" } + " *"
            startFixture(model, text)
            rule.runOnIdle { model.gameLibrary.jump(513); model.gameLibrary.analyze() }
            assertEquals(0, calls)
            assertTrue((model.gameLibrary.state.value.engineAnalysis as EngineAnalysisUiState.Error).message.contains("earlier repetitions were not discarded"))
            assertEquals("Nf3", teaching(model).facts.idea.san)
            rule.runOnIdle { model.gameLibrary.jump(516) }
            assertTrue(study(model).replay.atEnd)
            assertEquals(516, study(model).replay.moves.size)
        } finally { rule.runOnIdle { owner.clear() } }
    }

    private fun checkedFixture(position: AnalysisPosition, original: String?, budget: AnalysisBudget): EngineAnalysis {
        val board = position.board()
        fun line(uci: String, cp: Int) = AnalyzedLine(1, 4, 40, 10, EngineScore.Centipawns(cp), listOf(uci), listOf(board.san(ChessMove.fromUci(uci))))
        val best = board.legalMoves().first().uci
        val candidates = AnalysisSearch(listOf(line(best, 12)), best, minOf(budget.multiPv, board.legalMoves().size), false)
        val compared = original?.let { AnalysisSearch(listOf(line(it, 10)), it, 1, true) }
        return EngineAnalysis(position, budget, EngineIdentity("Synthetic test engine", "1", "a".repeat(64), mapOf("nn-aaaaaaaaaaaa.nnue" to "b".repeat(64))), board.sideToMove, candidates, compared)
    }
}
