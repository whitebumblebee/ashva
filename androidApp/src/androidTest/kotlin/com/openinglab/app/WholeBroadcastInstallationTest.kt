// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.content.BundledContent
import com.openinglab.app.ui.AppViewModel
import com.openinglab.app.ui.ObservedRepliesUiState
import com.openinglab.app.ui.screens.OfflineLibraryScreen
import com.openinglab.app.ui.theme.OpeningLabTheme
import com.openinglab.shared.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Unique test-only Room file on the isolated device; never installs this sample into user storage. */
@RunWith(AndroidJUnit4::class)
class WholeBroadcastInstallationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun wholeMonthInstallsFromNativeScreenShowsDispositionsAndColdRebuildsObservations() {
        val started = System.nanoTime()
        fun phase(name: String) = Log.i("AshvaArchiveTest", "$name: ${(System.nanoTime() - started) / 1_000_000} ms")
        val context = rule.activity.applicationContext
        val name = "whole-broadcast-test-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name)
        val store = RoomLearningStore(db)
        val owner = ViewModelStore()
        val secondOwner = ViewModelStore()
        val selection = RepertoireSelection("ruy-lopez", "WHITE", "ruy-main")
        runBlocking {
            store.install(BundledContent.read(context.assets, BundledContent.choices.first()))
            store.selectRepertoire(selection)
        }
        phase("taxonomy installed")
        lateinit var vm: AppViewModel
        rule.runOnIdle {
            vm = AppViewModel(SavedStateHandle(), learningStore = store, packReader = { BundledContent.read(context.assets, it) })
            owner.put("whole-month", vm)
            rule.activity.setContent { OpeningLabTheme {
                OfflineLibraryScreen(vm.uiState.collectAsState().value, vm::installPack, {}, Modifier)
            } }
        }
        try {
            rule.onNodeWithTag("offline-library-list").performScrollToKey("lichess-broadcast-2020-01")
            rule.onNodeWithTag("install-lichess-broadcast-2020-01").performScrollTo().performClick()
            rule.waitUntil(180_000) { vm.uiState.value.packs.any { it.sourceId == "lichess-broadcast-2020-01" && it.state in setOf("DOWNLOADED", "ERROR") } }
            assertNull(vm.uiState.value.packError)
            phase("January installed")
            rule.onNodeWithTag("pack-status-lichess-broadcast-2020-01").performScrollTo().assertTextContains("Installed for offline use")
            rule.runOnIdle { vm.setDeveloperMode(true) }
            rule.onNodeWithTag("offline-library-list").performScrollToNode(hasTestTag("pack-dispositions-lichess-broadcast-2020-01"))
            rule.onNodeWithTag("pack-dispositions-lichess-broadcast-2020-01").performScrollTo().assertTextEquals(
                "952 inputs attempted · 0 duplicates · 95 quarantined. Rejected scores are excluded from observations and teaching; their disposition remains in the source pack.")
            rule.waitUntil(180_000) { vm.uiState.value.observedReplies is ObservedRepliesUiState.Ready }
            val ready = vm.uiState.value.observedReplies as ObservedRepliesUiState.Ready
            phase("observations ready")
            assertEquals(857, ready.index.totalScores)
            assertEquals("98c81851574b0d94f43b8a382e6a92df19328db2b8ced7e48d5c25ab50ff4c63", ready.index.sources.single().manifestSha256)
            assertEquals(857, runBlocking { store.games().size })
            assertEquals(listOf(selection), runBlocking { store.repertoires() })
            assertNull(runBlocking { store.latestBookmark() })
            clearTestViewModels(owner) { rule.runOnIdle(it) }
            db.close()
            val reopened = createAndroidLearningDatabase(context, name)
            try {
                lateinit var cold: AppViewModel
                rule.runOnIdle {
                    cold = AppViewModel(SavedStateHandle(), learningStore = RoomLearningStore(reopened))
                    secondOwner.put("cold-whole-month", cold)
                }
                rule.waitUntil(180_000) { cold.uiState.value.observedReplies is ObservedRepliesUiState.Ready }
                val restored = cold.uiState.value.observedReplies as ObservedRepliesUiState.Ready
                phase("cold observations ready")
                assertEquals(857, restored.index.totalScores)
                assertEquals(ready.index.sources, restored.index.sources)
                assertEquals(listOf(selection), runBlocking { RoomLearningStore(reopened).repertoires() })
                assertNull(runBlocking { RoomLearningStore(reopened).latestBookmark() })
            } finally {
                clearTestViewModels(secondOwner) { rule.runOnIdle(it) }
                reopened.close()
            }
        } finally {
            clearTestViewModels(owner, secondOwner) { rule.runOnIdle(it) }
            db.close()
            context.deleteDatabase(name) // only this UUID-named test database
        }
    }
}
