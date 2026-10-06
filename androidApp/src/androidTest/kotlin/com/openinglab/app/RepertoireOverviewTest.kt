// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.ui.*
import com.openinglab.app.ui.screens.MyRepertoiresScreen
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

/** Use an isolated exact-serial emulator; never the owner's learning install. */
@RunWith(AndroidJUnit4::class)
class RepertoireOverviewTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun policy(id: String, side: PieceColor): RepertoirePolicy {
        val book = RepertoireBook(LessonGraph.fromOpening(OpeningCatalog.byId(id)))
        return book.seed(side, book.graph.originalPathId)
    }
    private fun click(tag: String) {
        if (tag in setOf("my-repertoires", "gm-game-library", "offline-library"))
            rule.onNodeWithTag("home-list").performScrollToKey("home-secondary-links")
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    @Test fun combinedColorsConflictsAndExactEditorLinkDoNotRewriteSavedChoices() {
        val context = rule.activity.applicationContext
        val name = "overview-test-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name); val store = RoomLearningStore(db)
        val owner = ViewModelStore()
        val ruy = policy("ruy-lopez", PieceColor.WHITE)
        val london = policy("london", PieceColor.WHITE)
        val black = policy("ruy-lopez", PieceColor.BLACK)
        runBlocking { listOf(ruy, london, black).forEach { store.saveRepertoirePolicy(it) } }
        val saved = SavedStateHandle(mapOf("repertoire_cursor" to Bundle().apply {
            putString("id", ruy.id); putString("path", "ruy-main"); putInt("ply", 12)
        }))
        lateinit var vm: AppViewModel
        try {
            rule.runOnIdle {
                vm = AppViewModel(saved, learningStore = store); owner.put("test", vm)
                rule.activity.setContent { OpeningLabTheme { OpeningLabApp(vm) } }
            }
            rule.waitUntil(20_000) { vm.uiState.value.repertoirePolicies.size == 3 && vm.uiState.value.persistenceStatus != "Loading saved lesson…" }
            click("my-repertoires")
            rule.waitUntil(20_000) { vm.uiState.value.repertoireOverview is RepertoireOverviewUiState.Ready }
            rule.onNodeWithTag("overview-summary").performScrollTo().assertTextContains("WHITE · 2 saved family policies", substring = true)
            rule.onNodeWithTag("overview-conflicts").assertTextContains("preferred-move conflicts", substring = true)
            val groups = (vm.uiState.value.repertoireOverview as RepertoireOverviewUiState.Ready).groups
            assertTrue(groups.single { it.side == PieceColor.WHITE }.conflicts.isNotEmpty())
            click("overview-side-BLACK")
            rule.onNodeWithTag("overview-summary").assertTextContains("BLACK · 1 saved family policies", substring = true)
            rule.onNodeWithTag("overview-conflicts").assertTextContains("0 preferred-move conflicts", substring = true)
            click("overview-side-WHITE")
            click("overview-edit-${ruy.id}-e2e4")
            rule.waitUntil(20_000) { vm.uiState.value.repertoireEditor?.policy?.id == ruy.id && !vm.uiState.value.repertoireLoading }
            assertEquals(0, vm.uiState.value.repertoireEditor?.ply) // Explicit conflict link overrides the old cursor=12.
            assertEquals("ruy-main", vm.uiState.value.repertoireEditor?.pathId)
            rule.onNodeWithTag("repertoire-position").performScrollTo().assertTextContains("Position 0/", substring = true)
            runBlocking { for (p in listOf(ruy, london, black)) assertEquals(p, store.repertoirePolicy(p.id)) }
        } finally { clearTestViewModels(owner) { rule.runOnIdle(it) }; db.close(); context.deleteDatabase(name) }
    }

    @Test fun unavailableAndChangedMembersSurviveColdReopenWithoutFakeCounts() {
        val context = rule.activity.applicationContext
        val name = "overview-cold-test-${UUID.randomUUID()}.db"
        val old = createAndroidLearningDatabase(context, name)
        val changed = policy("ruy-lopez", PieceColor.WHITE).copy(contentVersion = "f".repeat(64))
        val missing = policy("london", PieceColor.WHITE).copy(id = "missing-policy", lessonId = "source:missing-pack:Example")
        runBlocking { val store = RoomLearningStore(old); store.saveRepertoirePolicy(changed); store.saveRepertoirePolicy(missing) }
        old.close()
        val reopened = createAndroidLearningDatabase(context, name); val store = RoomLearningStore(reopened)
        val owner = ViewModelStore(); lateinit var vm: AppViewModel
        try {
            rule.runOnIdle {
                vm = AppViewModel(SavedStateHandle(), learningStore = store); owner.put("test", vm)
                rule.activity.setContent { OpeningLabTheme { OpeningLabApp(vm) } }
            }
            rule.waitUntil(20_000) { vm.uiState.value.repertoirePolicies.size == 2 && vm.uiState.value.persistenceStatus != "Loading saved lesson…" }
            click("my-repertoires")
            rule.waitUntil(20_000) { vm.uiState.value.repertoireOverview is RepertoireOverviewUiState.Ready }
            rule.onNodeWithTag("overview-summary").performScrollTo().assertTextContains("0 preferred positions", substring = true)
            rule.onNodeWithTag("overview-conflicts").assertTextContains("2 unavailable/changed families", substring = true)
            rule.onNodeWithTag("my-repertoires-scroll").performScrollToKey(changed.id)
            rule.onNodeWithTag("overview-member-${changed.id}").assertTextContains("does not validate", substring = true)
            runBlocking { assertEquals(changed, store.repertoirePolicy(changed.id)); assertEquals(missing, store.repertoirePolicy(missing.id)) }
        } finally { clearTestViewModels(owner) { rule.runOnIdle(it) }; reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun loadingErrorAndRetryAreExplicitWithoutImplyingEmptyRepertoire() {
        var retries = 0
        rule.runOnIdle { rule.activity.setContent { OpeningLabTheme {
            MyRepertoiresScreen(emptyList(), null, {}, {}, {}, overview = RepertoireOverviewUiState.Error, onRetry = { retries++ })
        } } }
        rule.onNodeWithTag("overview-error").performScrollTo().assertTextEquals("Overview unavailable; your choices are saved.")
        rule.onNodeWithContentDescription("More information: Overview unavailable; your choices are saved.").performScrollTo().performClick()
        rule.onNodeWithText("no families were silently omitted", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()
        click("overview-retry"); assertEquals(1, retries)
        rule.runOnIdle { rule.activity.setContent { OpeningLabTheme {
            MyRepertoiresScreen(emptyList(), null, {}, {}, {}, overview = RepertoireOverviewUiState.Loading)
        } } }
        rule.onNodeWithTag("overview-loading").performScrollTo().assertExists()
    }
}
