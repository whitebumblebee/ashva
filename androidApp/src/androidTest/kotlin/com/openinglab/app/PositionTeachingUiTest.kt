// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.ui.AppViewModel
import com.openinglab.app.ui.screens.PositionTeachingPanel
import com.openinglab.app.ui.theme.OpeningLabTheme
import com.openinglab.shared.chess.*
import com.openinglab.shared.model.PieceColor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PositionTeachingUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @org.junit.After fun resetDeveloperMode() { rule.runOnIdle { ViewModelProvider(rule.activity)[AppViewModel::class.java].setDeveloperMode(false) } }
    private fun click(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
    private fun waitReady() = rule.waitUntil(10_000) { rule.onAllNodesWithTag("position-teaching-ready").fetchSemanticsNodes().isNotEmpty() }

    @Test fun replayShowsBothColorEvidenceAndDoesNotChangeLessonOrPracticeHints() {
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        rule.waitUntil(30_000) { vm.uiState.value.persistenceStatus != "Loading saved lesson…" }
        rule.onNodeWithText("Explore").performClick()
        rule.onNodeWithText("Starter").performClick()
        rule.onNodeWithText("Ruy López").performScrollTo().performClick()
        rule.onNodeWithText("Play White").performScrollTo().performClick()
        rule.runOnIdle { vm.studyTrainer(); vm.jumpTrainer(5) }
        val snapshot = vm.uiState.value.trainer!!.replay.snapshot()
        click("position-teaching-toggle"); waitReady()
        rule.onNodeWithTag("position-teaching-ready").performScrollTo().assertTextContains("WHITE POV", substring = true)
        rule.onNodeWithTag("position-teaching-own").assertExists()
        rule.onNodeWithTag("position-teaching-opponent").assertExists()
        rule.runOnIdle { vm.setDeveloperMode(true) }
        rule.onNodeWithTag("position-teaching-provenance").performScrollTo().assertTextContains("Not engine evaluation", substring = true)
        assertEquals(snapshot, vm.uiState.value.trainer!!.replay.snapshot())
        click("flip-side"); waitReady()
        rule.onNodeWithTag("position-teaching-ready").performScrollTo().assertTextContains("BLACK POV", substring = true)
        assertEquals(snapshot.ply, vm.uiState.value.trainer!!.ply)
        click("flip-side"); click("practice-mode")
        rule.onNodeWithTag("position-teaching-toggle").assertDoesNotExist()
        rule.runOnIdle { vm.trainerTap("d2"); vm.trainerTap("d4") }
        assertEquals(0, vm.uiState.value.trainer!!.ply)
        assertEquals(setOf("e2", "e4"), vm.uiState.value.trainer!!.hintSquares)
    }

    @Test fun changedPositionsCancelOldFactsAndTerminalBoardHasNoInventedCandidate() {
        val french = listOf("e4", "e6", "d4", "d5", "e5").fold(BoardPosition.starting()) { b, s -> b.parseSanAndPlay(s).position }
        val mate = listOf("f3", "e5", "g4", "Qh4#").fold(BoardPosition.starting()) { b, s -> b.parseSanAndPlay(s).position }
        val board = mutableStateOf(french)
        rule.activity.setContent { OpeningLabTheme { PositionTeachingPanel(board.value, PieceColor.BLACK) } }
        rule.onNodeWithTag("position-teaching-toggle").performClick(); waitReady()
        rule.onNodeWithTag("position-teaching-breaks").assertTextContains("BLACK to move", substring = true)
        rule.runOnIdle { board.value = BoardPosition.starting(); board.value = french; board.value = mate }
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasTestTag("position-teaching-focus") and hasText("checkmate", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("position-teaching-breaks").assertTextContains("WHITE to move", substring = true)
        rule.onNodeWithText("No current legal central pawn-contact move fits this specific lens. This does not mean there is no useful plan or pawn break to prepare.").assertExists()
    }
}
