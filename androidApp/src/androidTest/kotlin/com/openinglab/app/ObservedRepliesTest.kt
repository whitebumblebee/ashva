// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.content.BundledContent
import com.openinglab.app.ui.*
import com.openinglab.app.ui.screens.RepertoireScreen
import com.openinglab.app.ui.theme.OpeningLabTheme
import com.openinglab.shared.content.*
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.storage.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Only the disposable exact-serial install; never the user's learning database/device. */
@RunWith(AndroidJUnit4::class)
class ObservedRepliesTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @org.junit.After fun resetDeveloperMode() { rule.runOnIdle { ViewModelProvider(rule.activity)[AppViewModel::class.java].setDeveloperMode(false) } }
    private fun sample(): ObservedGameSample {
        val bundle = BundledContent.read(rule.activity.assets, BundledContent.choices.single { it.sourceId == "lichess-broadcast-2020-04" })
        val manifest = Json.decodeFromString<ContentManifest>(bundle.manifest.decodeToString())
        val games = bundle.files.getValue("games.jsonl").decodeToString().lineSequence().filter { it.isNotBlank() }
            .map { Json.decodeFromString<GameRecord>(it) }.toList()
        return ObservedGameSample(InstalledPack(manifest, bundle.expectedManifestSha256,
            bundle.files.getValue("ATTRIBUTION.txt").decodeToString()), games)
    }

    @Test fun realSampleShowsCountsOutsideMovesAndSourceDetailsWithoutChangingChoices() {
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        rule.waitUntil(20_000) { vm.uiState.value.persistenceStatus != "Loading saved lesson…" }
        rule.onNodeWithTag("home-list").performScrollToKey("home-secondary-links")
        rule.onNodeWithTag("offline-library").performScrollTo().performClick()
        for (choice in BundledContent.choices.filter { it.sourceId in setOf("lichess-openings", "lichess-broadcast-2020-04") }) {
            rule.onNodeWithTag("offline-library-list").performScrollToKey(choice.sourceId)
            if (vm.uiState.value.packs.none { it.sourceId == choice.sourceId && it.state == "DOWNLOADED" })
                rule.onNodeWithTag("install-${choice.sourceId}").performScrollTo().performClick()
            rule.waitUntil(120_000) { vm.uiState.value.packs.any { it.sourceId == choice.sourceId && it.state == "DOWNLOADED" } }
        }
        rule.onNodeWithTag("offline-library-list").performScrollToIndex(0)
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Explore").performClick()
        rule.onNodeWithText("Starter").performClick()
        rule.onNodeWithText("Ruy López").performScrollTo().performClick()
        rule.onNodeWithText("Play White").performScrollTo().performClick()
        rule.runOnIdle { vm.studyTrainer(); vm.jumpTrainer(0) }
        rule.onNodeWithTag("build-repertoire").performScrollTo().performClick()
        rule.waitUntil(20_000) { vm.uiState.value.repertoireEditor != null }
        // MainActivity intentionally defers the index until an editor actually needs it.
        rule.waitUntil(60_000) { vm.uiState.value.observedReplies is ObservedRepliesUiState.Ready || vm.uiState.value.observedReplies is ObservedRepliesUiState.Error }
        assertTrue(vm.uiState.value.observedReplies.toString(), vm.uiState.value.observedReplies is ObservedRepliesUiState.Ready)
        val before = vm.uiState.value.repertoireEditor!!.policy
        rule.onNodeWithTag("repertoire-scroll").performScrollToKey("observed-replies")
        rule.onNodeWithTag("observed-denominator").performScrollTo().assertTextContains("79 / 79 distinct score records", substring = true)
        rule.onNodeWithTag("observed-choice-counts").performScrollTo().assertTextContains("26 selected", substring = true)
        rule.onNodeWithTag("repertoire-scroll").performScrollToKey("option:e2e4")
        rule.onNodeWithTag("observed-option-e2e4", useUnmergedTree = true).assertTextContains("26 / 79", substring = true)
        rule.onNodeWithTag("repertoire-scroll").performScrollToKey("observed-outside:d2d4")
        rule.onNodeWithTag("observed-outside-d2d4").assert(hasAnyDescendant(hasText("d4 · Observed: 45 / 79 scores with a reply")))
        rule.onNodeWithTag("observed-outside-d2d4").assert(hasClickAction().not())
        assertEquals(before, vm.uiState.value.repertoireEditor!!.policy)
        rule.onNodeWithTag("repertoire-scroll").performScrollToKey("observed-replies")
        rule.runOnIdle { vm.setDeveloperMode(true) }
        rule.onNodeWithTag("observed-source-details").performScrollTo().performClick()
        val source = (vm.uiState.value.observedReplies as ObservedRepliesUiState.Ready).index.sources.single()
        rule.onNodeWithTag("observed-provenance-${source.manifest.packId}").performScrollTo().assertTextContains(source.manifestSha256, substring = true)
        rule.onNodeWithTag("observed-open-sources").performScrollTo().performClick()
        rule.onNodeWithTag("offline-library-list").assertExists()
        rule.onNodeWithTag("offline-library-list").performScrollToIndex(0)
        rule.onNodeWithText("Back").performClick()
        assertEquals(before, vm.uiState.value.repertoireEditor!!.policy)
    }

    @Test fun missingLoadingErrorAndZeroObservationsAreDistinctAndActionable() {
        val owner = ViewModelStore()
        val vm = AppViewModel(SavedStateHandle())
        owner.put("states", vm)
        try {
            rule.runOnIdle { vm.openRepertoire("ruy-lopez", PieceColor.WHITE, "ruy-main", 5) }
            rule.waitUntil(20_000) { vm.uiState.value.repertoireEditor != null }
            val editor = vm.uiState.value.repertoireEditor!!
            val observations = mutableStateOf<ObservedRepliesUiState>(ObservedRepliesUiState.Missing)
            var sourcesClicked = 0; var retries = 0
            rule.runOnIdle { rule.activity.setContent { OpeningLabTheme {
                RepertoireScreen(editor, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}, observations.value,
                    { sourcesClicked++ }, { retries++ })
            } } }
            rule.onNodeWithTag("repertoire-scroll").performScrollToKey("observed-replies")
            rule.onNodeWithTag("observed-missing").performScrollTo().assertExists()
            rule.onNodeWithTag("observed-open-sources").performScrollTo().performClick()
            assertEquals(1, sourcesClicked)
            rule.runOnIdle { observations.value = ObservedRepliesUiState.Loading }
            rule.onNodeWithTag("observed-loading").performScrollTo().assertExists()
            rule.onNodeWithTag("observed-denominator").assertDoesNotExist()
            rule.runOnIdle { observations.value = ObservedRepliesUiState.Error }
            rule.onNodeWithTag("observed-error").performScrollTo().assertExists()
            rule.onNodeWithTag("observed-retry").performScrollTo().performClick()
            assertEquals(1, retries)
            val full = sample()
            val first = full.games.first() // Starts d4, so never reaches the Ruy position above.
            val single = full.copy(source = full.source.copy(manifest = full.source.manifest.copy(packId = "single-score-ui-fixture",
                coverage = full.source.manifest.coverage.copy(acceptedRecords = 1, gamePlies = first.uci.size))), games = listOf(first))
            val index = ObservedReplyIndex.build(listOf(single))
            assertEquals(0, index.at(editor.position.positionKey).scoresSeen)
            rule.runOnIdle { observations.value = ObservedRepliesUiState.Ready(index) }
            rule.onNodeWithTag("observed-zero").performScrollTo().assertExists()
            rule.onNodeWithTag("observed-denominator").performScrollTo().assertTextContains("0 / 1 distinct score records", substring = true)
            rule.onNodeWithTag("observed-missing").assertDoesNotExist()
        } finally { rule.runOnIdle { owner.clear() } }
    }

    @Test fun failedIndexCanRetryAndWithdrawnPackCannotPublishStaleCounts() {
        val sample = sample()
        val store = (rule.activity.application as OpeningLabApplication).learningStore
        val installed = MutableStateFlow(listOf(sample.source))
        val changes = MutableStateFlow(emptyList<PackAvailability>())
        val fail = AtomicBoolean(true)
        val failPacks = AtomicBoolean(false)
        val gate = AtomicReference<CompletableDeferred<Unit>?>(null)
        val fake = object : LearningStore by store {
            override val availability = changes
            // This fake owns synthetic install state, not MainActivity's live first-launch job.
            override suspend fun recoverInterruptedInstalls() = Unit
            override suspend fun activePacks(): List<InstalledPack> {
                check(!failPacks.get()) { "Synthetic active-version read failure" }
                return installed.value
            }
            override suspend fun games(packId: String?): List<GameRecord> {
                gate.get()?.await()
                check(!fail.get()) { "Synthetic read failure" }
                return sample.games
            }
        }
        val owner = ViewModelStore()
        lateinit var vm: AppViewModel
        try {
            rule.runOnIdle { vm = AppViewModel(SavedStateHandle(), learningStore = fake); owner.put("retry", vm) }
            rule.waitUntil(20_000) { vm.uiState.value.observedReplies == ObservedRepliesUiState.Error }
            fail.set(false)
            rule.runOnIdle { vm.retryObservedReplies() }
            rule.waitUntil(60_000) { vm.uiState.value.observedReplies is ObservedRepliesUiState.Ready }
            assertEquals(79, (vm.uiState.value.observedReplies as ObservedRepliesUiState.Ready).index.totalScores)
            failPacks.set(true)
            rule.runOnIdle { vm.retryObservedReplies() }
            rule.waitUntil(20_000) { vm.uiState.value.observedReplies == ObservedRepliesUiState.Error }
            failPacks.set(false)
            rule.runOnIdle { vm.retryObservedReplies() }
            rule.waitUntil(60_000) { vm.uiState.value.observedReplies is ObservedRepliesUiState.Ready }
            val pending = CompletableDeferred<Unit>(); gate.set(pending)
            rule.runOnIdle { vm.retryObservedReplies() }
            rule.waitUntil(20_000) { vm.uiState.value.observedReplies == ObservedRepliesUiState.Loading }
            installed.value = emptyList()
            changes.value = listOf(PackAvailability("fixture", "withdrawn", "MISSING", null, null))
            rule.waitUntil(20_000) { vm.uiState.value.observedReplies == ObservedRepliesUiState.Missing }
            pending.complete(Unit)
            rule.waitForIdle()
            assertEquals(ObservedRepliesUiState.Missing, vm.uiState.value.observedReplies)
        } finally { rule.runOnIdle { owner.clear() } }
    }
}
