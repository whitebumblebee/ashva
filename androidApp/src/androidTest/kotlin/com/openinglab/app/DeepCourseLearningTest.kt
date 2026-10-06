// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openinglab.app.content.BundledCourses
import com.openinglab.app.content.CourseFeedbackStore
import com.openinglab.app.ui.*
import com.openinglab.shared.course.DeepCourseCatalog
import com.openinglab.shared.course.DeepCourseChapterView
import com.openinglab.shared.lesson.LessonPathKind
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.storage.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class DeepCourseLearningTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]
    private fun ready() {
        rule.waitUntil(60_000) { vm.uiState.value.deepCourses.isNotEmpty() || vm.uiState.value.deepCourseError != null }
        assertNull(vm.uiState.value.deepCourseError)
    }
    private fun click(tag: String) {
        if (tag.startsWith("deep-") && rule.onAllNodesWithTag("deep-course").fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithTag("deep-course").performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }
    private val repertoire get() = vm.uiState.value.deepCourses.first { it.chapter.kind == "REPERTOIRE" }
    private fun openChapter(chapter: DeepCourseChapterView) {
        rule.onNodeWithTag("home-list").performScrollToNode(hasTestTag("deep-course-${chapter.chapter.id}"))
        click("deep-course-${chapter.chapter.id}")
    }

    @Test fun bundledCourseMatchesItsReviewedChecksumAndValidates() {
        val text = BundledCourses.read(rule.activity.assets)
        val catalog = DeepCourseCatalog(listOf(DeepCourseCatalog.parse(text)))
        assertTrue(catalog.chapters.isNotEmpty())
        for (chapter in catalog.chapters) {
            assertTrue(chapter.opening.variations.all { line -> line.steps.all { it.label.startsWith("Generated") } })
            if (chapter.chapter.kind == "REPERTOIRE") assertTrue(chapter.chapter.nodes.filter { n -> chapter.chapter.nodes.none { it.parent == n.id } }.all { it.verdict != null })
        }
    }

    @Test fun homeOpensChapterStudyShowsLabelsAndFlagsAreSavedLocally() {
        ready()
        val chapter = repertoire
        openChapter(chapter)
        rule.onNodeWithTag("deep-course").performScrollToNode(hasTestTag("deep-coverage"))
        rule.onNodeWithTag("deep-coverage").assertTextContains("lines", substring = true)
        if (chapter.chapter.variations.isNotEmpty()) click("deep-toggle-lines")
        rule.onNodeWithTag("deep-course").performScrollToNode(hasTestTag("deep-line-count"))
        rule.onNodeWithTag("deep-line-count").assertTextContains("of ${chapter.opening.variations.size} lines", substring = true)
        click("deep-filter-TRAP")
        val traps = chapter.opening.variations.count { chapter.roles[it.id] == "TRAP" || chapter.roles[it.id] == "PUNISH" }
        rule.onNodeWithTag("deep-line-count").assertTextContains("$traps of", substring = true)
        click("deep-filter-ALL")
        click("deep-study-main")
        rule.waitUntil(60_000) { vm.uiState.value.trainer?.opening?.id == chapter.opening.id && vm.uiState.value.trainer?.mode == LessonMode.STUDY }
        rule.onNodeWithTag("deep-course-coverage").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { vm.jumpTrainer(chapter.chapter.rootPly + 1) }
        rule.onNodeWithTag("explanation-label").performScrollTo().assertTextContains("Generated", substring = true)
        rule.onNodeWithTag("deep-full-idea-note").performScrollTo().assertIsDisplayed()
        val line = vm.uiState.value.trainer!!.replay.pathId
        click("flag-unclear")
        rule.waitUntil(10_000) { vm.uiState.value.courseFeedback.any { it.lineId == line && it.ply == chapter.chapter.rootPly + 1 && it.kind == "unclear" } }
        rule.onNodeWithTag("flag-message").performScrollTo().assertTextContains("Flag saved on this device", substring = true)
        assertTrue(vm.exportCourseFeedback().contains(line))
    }

    @Test fun weightedPracticeFollowsOneLineWithoutBranchPausesAndCompletes() {
        ready()
        val chapter = repertoire
        val side = chapter.course.learnerSide ?: PieceColor.WHITE
        val weighted = chapter.lineWeights.filterValues { it > 0 }
        val picks = (1..5_000).map { seed ->
            var roll = Random(seed).nextDouble() * weighted.values.sum()
            weighted.entries.firstOrNull { (_, w) -> roll -= w; roll <= 0 }?.key ?: weighted.keys.last()
        }
        // Allow sampling variation among the highest-weight lines; every pick is a real course line.
        assertTrue(picks.all { it in chapter.lineWeights })
        assertTrue("zero-weight lines must never be picked", picks.none { chapter.lineWeights.getValue(it) == 0.0 })
        val highestWeightLines = chapter.lineWeights.entries.sortedByDescending { it.value }.take(3).map { it.key }.toSet()
        val mostPickedLine = picks.groupingBy { it }.eachCount().maxBy { it.value }.key
        assertTrue("most-picked line must be among the three highest-weight lines", mostPickedLine in highestWeightLines)
        lateinit var chosen: String
        rule.runOnIdle { chosen = vm.practiceWeightedDeepLine(chapter.opening.id, side, Random(7))!! }
        rule.waitUntil(60_000) { vm.uiState.value.trainer?.replay?.pathId == chosen && !vm.uiState.value.lessonLoading }
        var guard = 0
        while (!vm.uiState.value.trainer!!.isComplete && guard++ < 200) {
            rule.waitUntil(10_000) { vm.uiState.value.trainer!!.let { it.isComplete || (!it.isOpponentThinking && it.position.sideToMove == side) } }
            val trainer = vm.uiState.value.trainer!!
            assertTrue("practice must not pause at opponent branches", trainer.branchOffers.isEmpty())
            if (trainer.isComplete) break
            val next = trainer.replay.nextMove!!.move
            rule.runOnIdle { vm.trainerTap(next.from); vm.trainerTap(next.to) }
        }
        val done = vm.uiState.value.trainer!!
        assertTrue(done.isComplete)
        assertEquals(chosen, done.replay.pathId)
        assertEquals(0, done.mistakes)
    }

    @Test fun gameChapterKeepsTheOriginalScoreSeparateFromEngineBranches() {
        ready()
        val game = vm.uiState.value.deepCourses.firstOrNull { it.chapter.kind == "GAME" } ?: return
        rule.runOnIdle { vm.startTrainer(game.opening.id, PieceColor.BLACK, study = true) }
        rule.waitUntil(60_000) { vm.uiState.value.trainer?.opening?.id == game.opening.id && vm.uiState.value.trainer?.mode == LessonMode.STUDY }
        val trainer = vm.uiState.value.trainer!!
        assertEquals(LessonPathKind.ORIGINAL_GAME, trainer.replay.path.kind)
        val branchPly = (0..trainer.replay.moves.size).first { ply -> trainer.replay.jump(ply).branches().isNotEmpty() }
        rule.runOnIdle { vm.jumpTrainer(branchPly) }
        val before = vm.uiState.value.trainer!!
        val branch = before.branchOffers.first()
        rule.runOnIdle { vm.switchTrainerBranch(branch.pathId, branch.targetPly) }
        assertEquals(LessonPathKind.ANALYZED_VARIATION, vm.uiState.value.trainer!!.replay.path.kind)
        rule.runOnIdle { vm.returnTrainerBranch() }
        assertEquals(before.position.toFen(), vm.uiState.value.trainer!!.position.toFen())
        assertEquals(LessonPathKind.ORIGINAL_GAME, vm.uiState.value.trainer!!.replay.path.kind)
    }

    @Test fun deepCourseBookmarkSurvivesColdRestart() {
        val context = rule.activity.applicationContext
        val name = "deep-course-test-${UUID.randomUUID()}.db"
        val db = createAndroidLearningDatabase(context, name)
        val source = { BundledCourses.read(context.assets) }
        val feedback = CourseFeedbackStore(File(context.cacheDir, "feedback-${UUID.randomUUID()}.json"))
        val firstOwner = ViewModelStore(); val nextOwner = ViewModelStore()
        lateinit var first: AppViewModel
        rule.runOnIdle { first = AppViewModel(SavedStateHandle(), learningStore = RoomLearningStore(db), deepCourseSource = source, courseFeedbackStore = feedback); firstOwner.put("fixture", first) }
        rule.waitUntil(60_000) { first.uiState.value.deepCourses.isNotEmpty() }
        val chapter = first.uiState.value.deepCourses.first { it.chapter.kind == "REPERTOIRE" }
        val line = chapter.opening.variations.last()
        rule.runOnIdle { first.startTrainer(chapter.opening.id, chapter.course.learnerSide ?: PieceColor.WHITE, line.id, study = true) }
        rule.waitUntil(60_000) { first.uiState.value.trainer?.replay?.pathId == line.id && first.uiState.value.trainer?.mode == LessonMode.STUDY }
        rule.runOnIdle { first.jumpTrainer(line.steps.size - 1) }
        rule.waitUntil(10_000) { first.uiState.value.persistenceStatus == "Saved for offline resume" }
        val before = first.uiState.value.trainer!!
        clearTestViewModels(firstOwner) { rule.runOnIdle(it) }; db.close()
        val reopened = createAndroidLearningDatabase(context, name)
        try {
            lateinit var restored: AppViewModel
            rule.runOnIdle { restored = AppViewModel(SavedStateHandle(), learningStore = RoomLearningStore(reopened), deepCourseSource = source, courseFeedbackStore = feedback); nextOwner.put("fixture", restored) }
            rule.waitUntil(120_000) { restored.uiState.value.trainer != null }
            val after = restored.uiState.value.trainer!!
            assertEquals(before.position, after.position)
            assertEquals(before.replay.pathId, after.replay.pathId)
            assertEquals(chapter.opening.id, after.opening.id)
        } finally {
            clearTestViewModels(nextOwner) { rule.runOnIdle(it) }; reopened.close(); context.deleteDatabase(name)
        }
    }

    @Test fun povToggleSwitchesTheStudySide() {
        ready()
        val chapter = repertoire
        assertNull(chapter.course.learnerSide)
        openChapter(chapter)
        click("deep-pov-black")
        rule.onNodeWithTag("deep-pov-black").assertTextContains("✓ Black", substring = true)
        click("deep-pov-white")
        rule.onNodeWithTag("deep-pov-white").assertTextContains("✓ White", substring = true)
        click("deep-pov-black")
        click("deep-study-main")
        rule.waitUntil(60_000) { vm.uiState.value.trainer?.opening?.id == chapter.opening.id && vm.uiState.value.trainer?.mode == LessonMode.STUDY }
        assertEquals(PieceColor.BLACK, vm.uiState.value.trainer!!.replay.playerSide)
    }

    @Test fun openingVariationCardShowsBothSidesWinningIdeas() {
        ready()
        val chapter = repertoire
        assertTrue(chapter.chapter.variations.isNotEmpty())
        openChapter(chapter)
        click("deep-variation-open-0")
        rule.onNodeWithTag("deep-ideas-title-w-0").performScrollTo().assertTextEquals("How White wins")
        rule.onNodeWithTag("deep-ideas-w-0").performScrollTo().assertTextEquals(chapter.chapter.variations.first().white.text)
        rule.onNodeWithTag("deep-ideas-title-b-0").performScrollTo().assertTextEquals("How Black wins")
        rule.onNodeWithTag("deep-ideas-b-0").performScrollTo().assertTextEquals(chapter.chapter.variations.first().black.text)
    }

    @Test fun replayExampleOpensTheOriginalGameLesson() {
        ready()
        val chapter = vm.uiState.value.deepCourses.first { c -> c.chapter.variations.any { it.white.examples.isNotEmpty() || it.black.examples.isNotEmpty() } }
        val index = chapter.chapter.variations.indexOfFirst { it.white.examples.isNotEmpty() || it.black.examples.isNotEmpty() }
        val v = chapter.chapter.variations[index]
        val white = v.white.examples.isNotEmpty()
        val example = (if (white) v.white else v.black).examples.first()
        val id = DeepCourseCatalog.exampleId(chapter.opening.id, index, white, 0)
        openChapter(chapter)
        click("deep-variation-open-$index")
        click("deep-example-${if (white) "w" else "b"}-$index-0")
        rule.waitUntil(60_000) { vm.uiState.value.trainer?.opening?.id == id && vm.uiState.value.trainer?.mode == LessonMode.STUDY }
        val replay = vm.uiState.value.trainer!!.replay
        assertEquals(LessonPathKind.ORIGINAL_GAME, replay.path.kind)
        assertEquals(example.uci, replay.moves.map { it.move.uci })
        assertEquals(if (white) PieceColor.WHITE else PieceColor.BLACK, replay.playerSide)
    }

    @Test fun studyShowsWhoPlayedAMasterMove() {
        ready()
        val chapter = repertoire
        val line = chapter.opening.mainLine
        val master = chapter.chapter.nodes.first { it.id in chapter.lineNodes.getValue(line.id) && (it.evidence?.masterGames ?: 0) > 0 }
        val players = DeepCourseCatalog.players(master)
        val ply = line.steps.indexOfFirst { it.uci == master.uci && it.players == players }
        assertTrue("master move must include strength-band counts", players.isNotBlank() && ply >= 0)
        openChapter(chapter)
        click("deep-study-main")
        rule.waitUntil(60_000) { vm.uiState.value.trainer?.opening?.id == chapter.opening.id && vm.uiState.value.trainer?.mode == LessonMode.STUDY }
        rule.runOnIdle { vm.jumpTrainer(ply + 1) }
        rule.onNodeWithTag("explanation-players").performScrollTo().assertTextEquals(players)
    }
}
