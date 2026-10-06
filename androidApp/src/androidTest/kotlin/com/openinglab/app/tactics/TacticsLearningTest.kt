// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.tactics

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.MainActivity
import com.openinglab.app.clearTestViewModels
import com.openinglab.app.ui.*
import com.openinglab.app.ui.theme.OpeningLabTheme
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.storage.*
import com.openinglab.shared.tactics.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Synthetic assets and UUID databases only. Compiled by Task B; Claude Code owns device execution. */
@RunWith(AndroidJUnit4::class)
class TacticsLearningTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun source(): String {
        val puzzles = (0 until 222).map { Puzzle("synthetic-$it", BoardPosition.START_FEN,
            listOf("e2e4", "e7e5", "g1f3", "b8c6"), 1200, listOf("fork")) }
        return Json.encodeToString(TacticsPack(puzzles = puzzles,
            sets = listOf(PuzzleSet("woodpecker-easy", "Woodpecker · Easy", puzzles.map { it.id }, "WOODPECKER"))))
    }
    private fun fixture(text: String = source(), test: (RoomLearningStore, AppViewModel) -> Unit) {
        val name = "tactics-test-${UUID.randomUUID()}.db"
        val context = rule.activity.applicationContext
        val db = createAndroidLearningDatabase(context, name); val store = RoomLearningStore(db); val owner = ViewModelStore()
        lateinit var vm: AppViewModel
        try {
            rule.runOnIdle {
                vm = AppViewModel(SavedStateHandle(), learningStore = store, tacticsSource = { text }); owner.put("tactics", vm)
                rule.activity.setContent { OpeningLabTheme { OpeningLabApp(vm) } }
            }
            rule.waitUntil(20_000) { !vm.tactics.state.value.loading }
            test(store, vm)
        } finally { clearTestViewModels(owner) { rule.runOnIdle(it) }; db.close(); context.deleteDatabase(name) }
    }
    private fun click(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
    private fun ready(vm: AppViewModel, ordinal: Int) = rule.waitUntil(10_000) {
        vm.tactics.state.value.puzzle?.let { it.ordinal == ordinal && it.phase == PuzzlePhase.READY } == true
    }
    @Test fun tacticsTabFirstSolutionMissAndResumeUseDurableFirstAttempts() = fixture { store, vm ->
        rule.onNodeWithText("Tactics").performClick()
        click("tactics-set-woodpecker-easy"); click("tactics-start"); ready(vm, 0)
        rule.runOnIdle { vm.tactics.toggleAutoNext(); vm.tactics.submit(ChessMove.fromUci("e7e5")) }
        rule.waitUntil(10_000) { vm.tactics.state.value.puzzle?.position?.lastMove?.uci == "g1f3" }
        ready(vm, 0)
        rule.runOnIdle { vm.tactics.submit(ChessMove.fromUci("b8c6")) }
        rule.waitUntil(10_000) { vm.tactics.state.value.history.attempts.size == 1 && !vm.tactics.state.value.saving }
        assertTrue(runBlocking { store.tactics.load().attempts.single().correct })
        click("tactics-next"); ready(vm, 1)
        rule.runOnIdle { vm.tactics.submit(ChessMove.fromUci("a7a6")) }
        rule.waitUntil(10_000) { vm.tactics.state.value.history.attempts.size == 2 && !vm.tactics.state.value.saving }
        rule.onNodeWithTag("tactics-feedback").assertTextEquals("Miss · the move was e5")
        assertFalse(runBlocking { store.tactics.load().attempts.last().correct })
        click("tactics-next"); ready(vm, 2); click("tactics-exit")
        rule.onNodeWithTag("tactics-start").assertTextContains("Resume cycle 1 (2/222)")
        click("tactics-start"); ready(vm, 2)
        rule.onNodeWithTag("tactics-progress").assertTextEquals("3 / 222")
        assertEquals(2, runBlocking { store.tactics.load().attempts.size })
    }
    @Test fun mistakesPracticeDoesNotChangeTheRecordedMissOrCycleTime() = fixture { store, vm ->
        rule.runOnIdle { vm.selectTab(MainTab.TACTICS) }; click("tactics-set-woodpecker-easy"); click("tactics-start"); ready(vm, 0)
        rule.runOnIdle { vm.tactics.submit(ChessMove.fromUci("a7a6")) }
        rule.waitUntil(10_000) { !vm.tactics.state.value.saving && vm.tactics.state.value.history.attempts.size == 1 }
        val before = runBlocking { store.tactics.load() }
        click("tactics-exit"); click("tactics-mistakes-1"); ready(vm, 0)
        rule.runOnIdle { vm.tactics.toggleAutoNext(); vm.tactics.submit(ChessMove.fromUci("e7e5")) }; ready(vm, 0)
        rule.runOnIdle { vm.tactics.submit(ChessMove.fromUci("b8c6")) }
        rule.waitUntil(10_000) { vm.tactics.state.value.puzzle?.phase == PuzzlePhase.SOLVED }
        assertEquals(before, runBlocking { store.tactics.load() })
    }
    @Test fun hiddenPuzzleTimeIsExcludedAndUnfinishedTimeSurvivesResume() {
        val name = "tactics-clock-${UUID.randomUUID()}.db"; val context = rule.activity.applicationContext
        val db = createAndroidLearningDatabase(context, name); val store = RoomTacticsStore(db)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var clock = 0L; lateinit var controller: TacticsController
        try {
            val text = source()
            rule.runOnIdle { controller = TacticsController(store, scope, { text }, elapsed = { clock }, now = { 1000 })
                rule.activity.setContent { OpeningLabTheme { TacticsTab(controller) } } }
            rule.waitUntil(20_000) { !controller.state.value.loading }
            click("tactics-set-woodpecker-easy"); click("tactics-start")
            rule.waitUntil(10_000) { controller.state.value.puzzle?.phase == PuzzlePhase.READY }
            rule.runOnIdle { clock = 5000; controller.setVisible(false) }
            assertEquals(5000L, controller.state.value.activeMs)
            rule.runOnIdle { clock = 95_000; controller.setVisible(true); clock = 96_000; controller.setVisible(false) }
            assertEquals(6000L, controller.state.value.activeMs)
            rule.waitUntil(10_000) { runBlocking { store.load().cycles.single().activeMs == 6000L } }
            rule.runOnIdle { controller.back(); controller.startCycle() }
            rule.waitUntil(10_000) { !controller.state.value.saving && controller.state.value.page == TacticsPage.PUZZLE }
            assertEquals(6000L, controller.state.value.activeMs)
        } finally { runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }; db.close(); context.deleteDatabase(name) }
    }
    @Test fun absentOrUnpinnedPackHasAGracefulFailureState() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var controller: TacticsController
        try {
            rule.runOnIdle { controller = TacticsController(null, scope, { BundledTactics.read(rule.activity.assets, "PENDING") })
                rule.activity.setContent { OpeningLabTheme { TacticsTab(controller) } } }
            rule.waitUntil(10_000) { !controller.state.value.loading }
            rule.onNodeWithTag("tactics-pack-error").assertExists()
        } finally { runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() } }
    }

    @Test fun wrongMoveShowsCaptureSanWithCheckAndKeepsThePuzzlePosition() {
        val puzzle = Puzzle("san-check", "4k3/3r4/8/8/8/8/8/3RK3 b - - 0 1", listOf("d7d8", "d1d8"), 1200, listOf("fork"))
        val pack = TacticsPack(puzzles = listOf(puzzle), sets = listOf(PuzzleSet("san-set", "SAN fixture", listOf(puzzle.id), "THEME")))
        fixture(Json.encodeToString(pack)) { store, vm ->
            rule.runOnIdle { vm.selectTab(MainTab.TACTICS) }
            click("tactics-set-san-set"); click("tactics-start"); ready(vm, 0)
            val before = vm.tactics.state.value.puzzle!!.position
            rule.runOnIdle { vm.tactics.submit(ChessMove.fromUci("e1f1")) }
            rule.waitUntil(10_000) { vm.tactics.state.value.puzzle?.phase == PuzzlePhase.WRONG && !vm.tactics.state.value.saving }
            rule.onNodeWithTag("tactics-feedback").assertTextEquals("Miss · the move was Rxd8+")
            assertEquals(before, vm.tactics.state.value.puzzle!!.position)
            assertFalse(runBlocking { store.tactics.load().attempts.single().correct })
            click("tactics-solution")
            rule.waitUntil(10_000) { vm.tactics.state.value.puzzle?.phase == PuzzlePhase.SOLUTION_DONE }
            rule.onNodeWithTag("tactics-feedback").assertTextEquals("Solution complete")
            assertEquals("d1d8", vm.tactics.state.value.puzzle!!.position.lastMove!!.uci)
        }
    }
}
