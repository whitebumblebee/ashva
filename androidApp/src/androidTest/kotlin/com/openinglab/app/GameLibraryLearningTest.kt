// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.Lifecycle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.ui.*
import com.openinglab.shared.games.*
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.storage.*
import com.openinglab.shared.content.SourceKind
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** UUID fixtures and connected tests belong only on the isolated exact-serial emulator. */
@RunWith(AndroidJUnit4::class)
class GameLibraryLearningTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]
    private fun click(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
    private fun ready(model: AppViewModel = vm): GameLibraryUiState.Ready {
        rule.waitUntil(60_000) { (model.gameLibrary.state.value.library as? GameLibraryUiState.Ready)?.searching == false }
        return model.gameLibrary.state.value.library as GameLibraryUiState.Ready
    }
    private fun broadcastInstalled() {
        rule.waitUntil(120_000) { vm.uiState.value.sourcedOpenings.size == 149 }
        if (vm.uiState.value.installedPacks.none { it.manifest.source.id == "lichess-broadcast-2020-04" })
            rule.runOnIdle { vm.installPack("lichess-broadcast-2020-04") }
        rule.waitUntil(120_000) { vm.uiState.value.installedPacks.any { it.manifest.source.id == "lichess-broadcast-2020-04" } || vm.uiState.value.packError != null }
        assertNull(vm.uiState.value.packError)
    }

    @Test fun nativeSourceSearchFullOriginalBothColorsAndRecreatedLibrary() {
        broadcastInstalled()
        click("gm-game-library")
        val score = ready().library.scores.filterIsInstance<LibraryScore.Broadcast>().first { it.origins.any { pack -> pack.manifest.source.id == "lichess-broadcast-2020-04" } }
        rule.onNodeWithTag("game-search").performScrollTo().performTextReplacement(score.white.name)
        assertTrue(ready().scores.any { it.id == score.id })
        click("game-filters")
        rule.onNodeWithTag("game-event-filter").performScrollTo().performTextReplacement(score.event)
        assertTrue(ready().scores.any { it.id == score.id })
        click("game-clear-filters"); ready()
        rule.onNodeWithTag("game-library-list").performScrollToKey("score-${score.id}")
        click("game-open-BLACK-${score.id}")
        rule.waitUntil(60_000) { vm.gameLibrary.state.value.study != null && !vm.gameLibrary.state.value.studyLoading }
        val original = requireNotNull(vm.gameLibrary.state.value.study)
        assertEquals(score.san, original.replay.moves.map { it.san })
        assertEquals(PieceColor.BLACK, original.replay.playerSide)
        assertEquals(score.canonicalPgn, original.score.canonicalPgn)
        rule.onNodeWithTag("game-original").assertTextContains("ORIGINAL GAME", substring = true)
        click("game-last"); assertEquals(score.san.size, vm.gameLibrary.state.value.study?.replay?.ply)
        click("game-first"); click("game-next"); click("game-flip")
        assertEquals(PieceColor.WHITE, vm.gameLibrary.state.value.study?.replay?.playerSide)
        assertEquals(1, vm.gameLibrary.state.value.study?.replay?.ply)
        click("game-play")
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(requireNotNull(vm.gameLibrary.state.value.study).isPlaying)
        val pausedPly = requireNotNull(vm.gameLibrary.state.value.study).replay.ply
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        val deadline = SystemClock.elapsedRealtime() + 1300
        rule.waitUntil(4000) { SystemClock.elapsedRealtime() >= deadline }
        assertEquals(pausedPly, vm.gameLibrary.state.value.study?.replay?.ply)
        rule.onNodeWithTag("game-exact-source").performScrollTo().assertTextContains(original.reference.manifestSha256!!, substring = true)
        click("game-back")
        rule.activityRule.scenario.recreate()
        ready()
        rule.onNodeWithTag("game-library-list").assertExists()
        assertEquals(score.canonicalPgn, vm.gameLibrary.state.value.study?.score?.canonicalPgn)
        assertFalse(requireNotNull(vm.gameLibrary.state.value.study).isPlaying)
    }

    @Test fun coldPublicReplayUsesRetainedSourceNotActivePointerAndBadHashCannotShowOldGame() {
        broadcastInstalled()
        val store = (rule.activity.application as OpeningLabApplication).learningStore
        rule.runOnIdle { vm.gameLibrary.openLibrary() }
        val score = ready().library.scores.filterIsInstance<LibraryScore.Broadcast>().first()
        rule.runOnIdle { vm.gameLibrary.start(score.id, PieceColor.BLACK) }
        rule.waitUntil(60_000) { vm.gameLibrary.state.value.study?.score?.id == score.id }
        rule.runOnIdle { vm.gameLibrary.jump(7) }
        rule.waitUntil(10_000) { vm.uiState.value.persistenceStatus == "Saved for offline resume" }
        val bookmark = runBlocking { store.latestBookmark() }!!
        val before = requireNotNull(vm.gameLibrary.state.value.study)
        // An unavailable active sample is not permission to substitute a different score/version.
        val retainedOnly = object : LearningStore by store {
            override suspend fun activePacks() = store.activePacks().filter { it.manifest.source.kind != SourceKind.BROADCAST_GAMES }
        }
        val owner = ViewModelStore()
        try {
            lateinit var cold: AppViewModel
            rule.runOnIdle { cold = AppViewModel(SavedStateHandle(), learningStore = retainedOnly); owner.put("test", cold) }
            rule.waitUntil(10_000) { cold.gameLibrary.state.value.study != null }
            val restored = requireNotNull(cold.gameLibrary.state.value.study)
            assertEquals(before.reference, restored.reference); assertEquals(before.replay.position, restored.replay.position)
            assertEquals(score.canonicalPgn, restored.score.canonicalPgn); assertEquals(7, restored.replay.ply)
            val bad = bookmark.copy(gameReference = bookmark.gameReference!!.copy(manifestSha256 = "b".repeat(64)))
            runBlocking { withContext(Dispatchers.Main) { cold.gameLibrary.restore(bad) } }
            assertNull(cold.gameLibrary.state.value.study)
            assertTrue(cold.gameLibrary.state.value.message!!.contains("no newer score was substituted"))
            assertEquals(bookmark, runBlocking { store.latestBookmark() })
        } finally { rule.runOnIdle { owner.clear() } }
    }

    @Test fun nativePrivateImportFollowFiltersAndRejectedImportKeepOriginal() {
        val name = "Synthetic-${UUID.randomUUID()}"
        click("gm-game-library"); click("game-import")
        val text = "[White \"$name\"]\n[Black \"Synthetic Opponent\"]\n[Event \"Private fixture\"]\n\n1. e4 {User comment} e5 2. Nf3 Nc6 *"
        rule.onNodeWithTag("game-pgn-input").performTextInput(text); rule.onNodeWithTag("game-pgn-confirm").performClick()
        rule.waitUntil(60_000) { !vm.gameLibrary.state.value.importing && (vm.gameLibrary.state.value.library as? GameLibraryUiState.Ready)?.library?.scores?.any { it.white.name == name } == true }
        rule.onNodeWithTag("game-search").performScrollTo().performTextReplacement(name)
        val score = ready().scores.single() as LibraryScore.Private
        val playerId = score.white.id
        rule.onNodeWithTag("game-library-list").performScrollToKey("player-$playerId")
        click("game-follow-$playerId")
        rule.waitUntil(10_000) { vm.gameLibrary.state.value.followers.any { it.id == playerId } }
        click("game-followed-only"); assertEquals(listOf(score.id), ready().scores.map { it.id })
        rule.onNodeWithTag("game-library-list").performScrollToKey("score-${score.id}")
        click("game-open-WHITE-${score.id}")
        rule.waitUntil(60_000) { vm.gameLibrary.state.value.study?.reference?.recordId == score.game.id }
        click("game-last"); assertEquals(4, vm.gameLibrary.state.value.study?.replay?.ply)
        click("game-first"); click("game-next")
        rule.onNodeWithText("USER-SUPPLIED COMMENTARY (not Ashva/GM-verified): User comment").performScrollTo().assertExists()
        click("game-speed"); click("game-play")
        rule.waitUntil(7000) { vm.gameLibrary.state.value.study?.replay?.atEnd == true }
        assertFalse(requireNotNull(vm.gameLibrary.state.value.study).isPlaying)
        assertEquals(score.san, vm.gameLibrary.state.value.study?.replay?.moves?.map { it.san })
        click("game-back")
        rule.onNodeWithTag("game-library-list").performScrollToKey("followed-$playerId")
        click("game-unfollow-$playerId")
        rule.waitUntil(10_000) { vm.gameLibrary.state.value.followers.none { it.id == playerId } }
        assertTrue(ready().scores.isEmpty())
        click("game-followed-only"); assertEquals(listOf(score.id), ready().scores.map { it.id })
        click("game-import"); rule.onNodeWithTag("game-pgn-input").performTextReplacement("1. e5 *"); rule.onNodeWithTag("game-pgn-confirm").performClick()
        rule.waitUntil(60_000) { !vm.gameLibrary.state.value.importing && vm.gameLibrary.state.value.message?.startsWith("Import rejected") == true }
        assertEquals(score.canonicalPgn, ready().library.score(score.id)?.canonicalPgn)
        assertTrue(ready().library.scores.filterIsInstance<LibraryScore.Broadcast>().none { it.white.name == name })
    }

    @Test fun coldPrivateBookmarkPreservesOpeningSelectionAndMissingScoreFailsClosed() {
        val context = rule.activity.applicationContext
        val name = "game-cold-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name)
        val store = RoomLearningStore(db)
        val record = runBlocking { store.importPrivateGame("[White \"Synthetic Alpha\"]\n[Black \"Synthetic Beta\"]\n\n1. d4 d5 2. c4 e6 *") }
        val firstOwner = ViewModelStore(); val coldOwner = ViewModelStore(); val missingOwner = ViewModelStore()
        lateinit var first: AppViewModel
        rule.runOnIdle { first = AppViewModel(SavedStateHandle(), learningStore = store); firstOwner.put("test", first); first.startTrainer("ruy-lopez", PieceColor.WHITE) }
        rule.waitUntil(10_000) { first.uiState.value.persistenceStatus == "Saved for offline resume" }
        val selections = runBlocking { store.repertoires() }
        rule.runOnIdle { first.gameLibrary.openLibrary() }; ready(first)
        rule.runOnIdle { first.gameLibrary.start(record.id, PieceColor.BLACK) }
        rule.waitUntil(10_000) { first.gameLibrary.state.value.study != null }
        rule.runOnIdle { first.gameLibrary.jump(3); first.gameLibrary.togglePlayback(); first.gameLibrary.leaveReplay() }
        rule.waitUntil(10_000) { first.uiState.value.persistenceStatus == "Saved for offline resume" }
        val before = requireNotNull(first.gameLibrary.state.value.study)
        val bookmark = runBlocking { store.latestBookmark() }!!
        assertEquals(selections, runBlocking { store.repertoires() })
        assertTrue(runBlocking { store.attempts(bookmark.lessonId) }.isEmpty())
        clearTestViewModels(firstOwner) { rule.runOnIdle(it) }; db.close()
        val reopened = createAndroidLearningDatabase(context, name)
        try {
            val nextStore = RoomLearningStore(reopened)
            lateinit var cold: AppViewModel
            rule.runOnIdle { cold = AppViewModel(SavedStateHandle(), learningStore = nextStore); coldOwner.put("test", cold) }
            rule.waitUntil(10_000) { cold.gameLibrary.state.value.study != null }
            val after = requireNotNull(cold.gameLibrary.state.value.study)
            assertEquals(before.reference, after.reference); assertEquals(before.replay.position, after.replay.position)
            assertEquals(PieceColor.BLACK, after.replay.playerSide); assertEquals(3, after.replay.ply); assertFalse(after.isPlaying)
            rule.waitUntil(10_000) { cold.gameLibrary.state.value.moveTeaching is OriginalMoveTeachingUiState.Ready }
            val coldFacts = (cold.gameLibrary.state.value.moveTeaching as OriginalMoveTeachingUiState.Ready).facts
            assertEquals(after.replay.lastMove!!.san, coldFacts.idea.san)
            assertEquals(after.replay.position.toFen(), coldFacts.afterFen)
            assertEquals(EngineAnalysisUiState.Idle, cold.gameLibrary.state.value.engineAnalysis)
            val unavailable = object : LearningStore by nextStore { override suspend fun privateGame(id: String): PrivateGameRecord? = null }
            lateinit var missing: AppViewModel
            rule.runOnIdle { missing = AppViewModel(SavedStateHandle(), learningStore = unavailable); missingOwner.put("test", missing) }
            rule.waitUntil(10_000) { !missing.gameLibrary.state.value.studyLoading && missing.gameLibrary.state.value.message != null }
            assertNull(missing.gameLibrary.state.value.study)
            assertEquals(bookmark, runBlocking { nextStore.latestBookmark() })
            rule.runOnIdle { cold.startTrainer("london", PieceColor.WHITE) }
            assertFalse(cold.gameLibrary.state.value.active)
            rule.waitUntil(10_000) { cold.uiState.value.persistenceStatus == "Saved for offline resume" }
            assertNull(runBlocking { nextStore.latestBookmark() }?.gameReference)
            assertEquals(record, runBlocking { nextStore.privateGame(record.id) })
        } finally {
            clearTestViewModels(coldOwner, missingOwner) { rule.runOnIdle(it) }; reopened.close()
            context.deleteDatabase(name) // UUID fixture only, never the owner's app database.
        }
    }

    @Test fun conflatedBookmarkWriterDoesNotReportOldReceiveAsNewestSavedRevision() {
        val context = rule.activity.applicationContext
        val name = "game-bookmark-writer-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name)
        val store = RoomLearningStore(db)
        val firstStarted = CompletableDeferred<Unit>(); val firstRelease = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>(); val secondRelease = CompletableDeferred<Unit>()
        var writes = 0
        val delayed = object : LearningStore by store {
            override suspend fun saveBookmark(bookmark: LessonBookmark) {
                writes++
                if (writes == 1) { firstStarted.complete(Unit); firstRelease.await() }
                if (writes == 2) { secondStarted.complete(Unit); secondRelease.await() }
                store.saveBookmark(bookmark)
            }
        }
        val owner = ViewModelStore()
        try {
            lateinit var model: AppViewModel
            rule.runOnIdle { model = AppViewModel(SavedStateHandle(), learningStore = delayed); owner.put("test", model) }
            rule.waitUntil(10_000) { model.uiState.value.persistenceStatus != "Loading saved lesson…" }
            // The suspended receiver already owns the first value while later sends conflate.
            rule.runOnIdle {
                model.startTrainer("ruy-lopez", PieceColor.WHITE)
                model.studyTrainer(); model.jumpTrainer(3); model.flipTrainerSide()
            }
            rule.waitUntil(10_000) { firstStarted.isCompleted }
            firstRelease.complete(Unit)
            rule.waitUntil(10_000) { secondStarted.isCompleted }
            assertEquals("Saving lesson…", model.uiState.value.persistenceStatus)
            secondRelease.complete(Unit)
            rule.waitUntil(10_000) { model.uiState.value.persistenceStatus == "Saved for offline resume" }
            val saved = runBlocking { store.latestBookmark() }!!
            assertEquals(3, saved.replay.ply); assertEquals(PieceColor.BLACK, saved.replay.playerSide)
            assertEquals("STUDY", saved.mode)
        } finally {
            firstRelease.complete(Unit); secondRelease.complete(Unit)
            clearTestViewModels(owner) { rule.runOnIdle(it) }; db.close()
            context.deleteDatabase(name) // Explicit UUID test fixture only.
        }
    }
}
