// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openinglab.app.content.BundledCourses
import com.openinglab.app.content.CourseFeedbackStore
import com.openinglab.app.ui.*
import com.openinglab.shared.course.DeepCourseCatalog
import com.openinglab.shared.course.DeepCourseChapterView
import com.openinglab.shared.course.CourseVariationTree
import com.openinglab.app.content.AppPreferences
import com.openinglab.app.content.CourseFeedback
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
    @org.junit.After fun resetDeveloperMode() { rule.runOnIdle { ViewModelProvider(rule.activity)[AppViewModel::class.java].setDeveloperMode(false) } }
    private val vm get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]
    private fun ready() {
        rule.waitUntil(60_000) { vm.uiState.value.deepCourseSummaries.isNotEmpty() || vm.uiState.value.deepCourseError != null }
        assertNull(vm.uiState.value.deepCourseError)
        rule.runOnIdle { vm.setDeveloperMode(false) }
    }
    private fun click(tag: String) {
        rule.waitForIdle()
        if (rule.onAllNodesWithTag("flag-dialog").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag(tag).assertIsDisplayed().performClick()
            rule.waitForIdle()
            return
        }
        val list = listOf("deep-course", "deep-course-overview", "deep-all-lines", "deep-variation-screen", "learner-profile-screen")
            .firstOrNull { rule.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
        if (list != null)
            rule.onNodeWithTag(list).performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        rule.waitForIdle()
    }
    private fun settleBeforeNavigation() {
        // Finish finite Compose transitions before disposing an outgoing navigation entry.
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
    private fun prepareChapter(summary: com.openinglab.shared.course.DeepCourseChapterSummary, model: AppViewModel = vm): DeepCourseChapterView {
        rule.runOnIdle { model.loadDeepChapter(summary.openingId) }
        rule.waitUntil(60_000) { model.deepChapter(summary.openingId) != null || model.uiState.value.deepChapterErrors[summary.openingId] != null }
        assertNull(model.uiState.value.deepChapterErrors[summary.openingId])
        return requireNotNull(model.deepChapter(summary.openingId))
    }
    private val repertoire get() = prepareChapter(vm.uiState.value.deepCourseSummaries.first { it.chapter.kind == "REPERTOIRE" })
    private fun openChapter(chapter: DeepCourseChapterView) {
        val packTag = "deep-pack-${chapter.course.id}"
        rule.onNodeWithTag("home-list").performScrollToNode(hasTestTag(packTag))
        click(packTag)
        rule.onNodeWithTag("deep-course-overview").assertExists()
        click("deep-course-${chapter.chapter.id}")
    }
    private fun openVariation(chapter: DeepCourseChapterView, index: Int) {
        val tree = CourseVariationTree(chapter.chapter.variations)
        val ancestors = generateSequence(tree.parents[index]) { tree.parents[it] }.toList().reversed()
        // Roots are expanded by default; deeper ancestors need an explicit chevron tap.
        ancestors.drop(1).forEach { click("deep-expand-$it") }
        click("deep-variation-$index")
        rule.onNodeWithTag("deep-variation-screen").assertExists()
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

    @Test fun homeOverviewChapterAndAllLinesKeepSearchAndFilters() {
        ready()
        val chapter = repertoire
        openChapter(chapter)
        rule.onNodeWithTag("deep-coverage").assertDoesNotExist()
        click("deep-toggle-lines")
        rule.onNodeWithTag("deep-all-lines").assertExists()
        rule.onNodeWithTag("deep-line-count").assertTextContains("of ${chapter.opening.variations.size} lines", substring = true)
        click("deep-filter-TRAP")
        val traps = chapter.opening.variations.count { chapter.roles[it.id] == "TRAP" || chapter.roles[it.id] == "PUNISH" }
        rule.onNodeWithTag("deep-line-count").assertTextContains("$traps of", substring = true)
        click("deep-filter-ALL")
        rule.onNodeWithTag("deep-line-search").performTextInput("Berlin")
        rule.onNodeWithTag("deep-line-count").assertTextContains("${chapter.opening.variations.count { it.name.contains("Berlin", true) }} of", substring = true)
    }

    @Test fun provenanceIsHiddenByDefaultAndVisibleInDeveloperMode() {
        ready()
        rule.runOnIdle { vm.setDeveloperMode(false) }
        val chapter = repertoire
        openChapter(chapter)
        rule.onNodeWithTag("deep-label-policy").assertDoesNotExist()
        rule.onNodeWithText("How this text was made").assertDoesNotExist()
        rule.runOnIdle { vm.setDeveloperMode(true) }
        try {
            rule.onNodeWithTag("deep-course").performScrollToNode(hasTestTag("deep-label-policy"))
            rule.onNodeWithTag("deep-label-policy").assertIsDisplayed()
            click("deep-study-main")
            rule.waitUntil(60_000) { vm.uiState.value.trainer?.mode == LessonMode.STUDY && !vm.uiState.value.lessonLoading }
            rule.runOnIdle { vm.jumpTrainer(chapter.chapter.rootPly + 1) }
            rule.onNodeWithTag("explanation-label").performScrollTo().assertTextContains("Generated", substring = true)
            click("full-idea")
            rule.onNodeWithTag("deep-full-idea-note").performScrollTo().assertIsDisplayed()
            rule.runOnIdle { vm.setDeveloperMode(false) }
            rule.onNodeWithTag("explanation-label").assertDoesNotExist()
            rule.onNodeWithTag("deep-course-coverage").assertDoesNotExist()
            rule.onNodeWithTag("deep-full-idea-note").assertDoesNotExist()
        } finally { rule.runOnIdle { vm.setDeveloperMode(false) } }
    }

    @Test fun flagDialogSavesAnOptionalNoteLocally() {
        ready()
        val chapter = repertoire
        openChapter(chapter)
        click("deep-study-main")
        rule.waitUntil(60_000) { vm.uiState.value.trainer?.mode == LessonMode.STUDY && !vm.uiState.value.lessonLoading }
        rule.runOnIdle { vm.jumpTrainer(chapter.chapter.rootPly + 1) }
        val line = vm.uiState.value.trainer!!.replay.pathId
        rule.onNodeWithTag("explanation-label").assertDoesNotExist()
        click("flag-explanation")
        rule.onNodeWithTag("flag-dialog").assertIsDisplayed()
        click("flag-other")
        rule.onNodeWithTag("flag-note").performTextInput("Please explain the pawn break.")
        click("flag-save")
        rule.waitUntil(10_000) { vm.uiState.value.courseFeedback.any { it.lineId == line && it.ply == chapter.chapter.rootPly + 1 && it.kind == "other" && it.note == "Please explain the pawn break." } }
        rule.onNodeWithText("Thanks — saved on this device").assertIsDisplayed()
        assertTrue(vm.exportCourseFeedback().contains("Please explain the pawn break."))
    }

    @Test fun developerPreferenceAndOldFeedbackRemainBackwardCompatible() {
        val context = rule.activity.applicationContext
        val name = "preferences-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
        try {
            val first = AppPreferences(preferences)
            assertFalse(first.developerMode)
            first.developerMode = true
            assertTrue(AppPreferences(context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)).developerMode)
            val old = """[{"courseLessonId":"c","lineId":"l","ply":1,"san":"e4","kind":"wrong","text":"Idea","label":"Generated","createdAtMillis":1}]"""
            val file = File(context.cacheDir, "flags-${UUID.randomUUID()}.json")
            try {
                file.writeText(old)
                val store = CourseFeedbackStore(file)
                assertEquals("", store.all().single().note)
                store.add(store.all().single().copy(note = "Short note"))
                assertEquals("Short note", CourseFeedbackStore(file).all().single().note)
            } finally { file.delete() }
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun profileAboutHasSourcesAndDeveloperOnlyFeedback() {
        ready()
        rule.runOnIdle { vm.selectTab(MainTab.PROFILE) }
        rule.onNodeWithTag("profile-content-feedback").assertDoesNotExist()
        click("developer-mode")
        assertTrue(vm.uiState.value.developerMode)
        rule.onNodeWithTag("profile-content-feedback").assertExists()
        click("profile-sources")
        rule.onNodeWithTag("sources-licences").assertExists()
        rule.onNodeWithTag("sources-licences").performScrollToNode(hasText("Stockfish", substring = false))
        rule.onNodeWithText("Stockfish", substring = false).assertIsDisplayed()
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
        val game = prepareChapter(vm.uiState.value.deepCourseSummaries.firstOrNull { it.chapter.kind == "GAME" } ?: return)
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
        rule.waitUntil(60_000) { first.uiState.value.deepCourseSummaries.isNotEmpty() }
        val chapter = prepareChapter(first.uiState.value.deepCourseSummaries.first { it.chapter.kind == "REPERTOIRE" }, first)
        val line = chapter.opening.variations.last()
        val anchor = chapter.chapter.rootPly.coerceIn(1, line.steps.size - 1)
        val side = chapter.lessonGraph().start(PieceColor.WHITE, line.id).jump(anchor).position.sideToMove
        rule.runOnIdle { first.startTrainer(chapter.opening.id, side, line.id, study = true, startPly = anchor) }
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
            assertEquals(anchor, after.practiceStartPly)
            rule.runOnIdle { restored.practiceTrainer() }
            assertEquals(anchor, restored.uiState.value.trainer!!.ply)
            assertEquals(chapter.lessonGraph().start(side, line.id).jump(anchor).position, restored.uiState.value.trainer!!.position)
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
        rule.onNodeWithTag("deep-pov-black").assertIsSelected()
        click("deep-pov-white")
        rule.onNodeWithTag("deep-pov-white").assertIsSelected()
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
        openVariation(chapter, 0)
        rule.onNodeWithTag("deep-ideas-title-w-0").performScrollTo().assertTextEquals("How White wins")
        val variation = chapter.chapter.variations.first()
        fun assertIdeas(white: Boolean) {
            val ideas = if (white) variation.white else variation.black
            val tag = "deep-ideas-${if (white) "w" else "b"}-0"
            rule.onNodeWithTag("deep-variation-screen").performScrollToNode(hasTestTag(tag))
            if (ideas.plan.isBlank() && ideas.patterns.isNotEmpty()) rule.onNodeWithTag(tag).assertExists()
            else rule.onNodeWithTag(tag).assertTextEquals(ideas.plan.ifBlank {
                if (ideas.text.contains("most distinctively played")) ideas.text else "No single plan stands out — see the model games."
            })
        }
        assertIdeas(true)
        click("deep-ideas-title-b-0")
        rule.onNodeWithTag("deep-ideas-title-b-0").performScrollTo().assertTextEquals("How Black wins")
        assertIdeas(false)
    }

    @Test fun replayExampleOpensTheOriginalGameLesson() {
        ready()
        val chapter = prepareChapter(vm.uiState.value.deepCourseSummaries.first { c -> c.chapter.variations.any { it.white.examples.isNotEmpty() || it.black.examples.isNotEmpty() } })
        val index = chapter.chapter.variations.indexOfFirst { it.white.examples.isNotEmpty() || it.black.examples.isNotEmpty() }
        val v = chapter.chapter.variations[index]
        val white = v.white.examples.isNotEmpty()
        val example = (if (white) v.white else v.black).examples.first()
        val id = DeepCourseCatalog.exampleId(chapter.opening.id, index, white, 0)
        settleBeforeNavigation()
        openChapter(chapter)
        settleBeforeNavigation()
        openVariation(chapter, index)
        if (!white) click("deep-ideas-title-b-$index")
        settleBeforeNavigation()
        click("deep-example-${if (white) "w" else "b"}-$index-0")
        rule.waitUntil(60_000) { vm.uiState.value.trainer?.opening?.id == id && vm.uiState.value.trainer?.mode == LessonMode.STUDY && !vm.uiState.value.lessonLoading }
        settleBeforeNavigation()
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

    @Test fun studyVariationOpensAfterItsDefiningMoveAndKeepsEarlierReplay() {
        ready()
        val chapter = repertoire
        val index = chapter.chapter.variations.indexOfFirst { v ->
            val line = chapter.lineThrough(v.nodeId) ?: return@indexOfFirst false
            (chapter.anchorPly(line, v.nodeId) ?: 0) > 1
        }
        assertTrue(index >= 0)
        val v = chapter.chapter.variations[index]
        val line = chapter.lineThrough(v.nodeId)!!
        val anchor = chapter.anchorPly(line, v.nodeId)!!
        openChapter(chapter); openVariation(chapter, index)
        click("deep-variation-study-$index")
        rule.waitUntil(60_000) { vm.uiState.value.trainer?.mode == LessonMode.STUDY && !vm.uiState.value.lessonLoading }
        val trainer = vm.uiState.value.trainer!!
        assertEquals(line, trainer.replay.pathId)
        assertEquals(anchor, trainer.ply)
        assertEquals(chapter.nodePositions.getValue(v.nodeId), trainer.position)
        assertEquals(v.path, trainer.replay.moves.take(anchor).map { it.san })
        rule.onNodeWithTag("build-repertoire").assertDoesNotExist()
        click("replay-previous")
        assertEquals(anchor - 1, vm.uiState.value.trainer!!.ply)
        click("replay-first")
        assertEquals(0, vm.uiState.value.trainer!!.ply)
        assertEquals(trainer.replay.moves, vm.uiState.value.trainer!!.replay.moves)
    }

    @Test fun practiceVariationSkipsTheApproachAndRestartRetainsTheAnchor() {
        ready()
        val chapter = repertoire
        val index = chapter.chapter.variations.indexOfFirst { v ->
            chapter.linesByNode[v.nodeId].orEmpty().all { line ->
                (chapter.anchorPly(line, v.nodeId) ?: 0) < chapter.opening.variations.first { it.id == line }.steps.size
            } && chapter.linesByNode[v.nodeId].orEmpty().isNotEmpty()
        }
        assertTrue(index >= 0)
        val v = chapter.chapter.variations[index]
        val position = chapter.nodePositions.getValue(v.nodeId)
        val side = position.sideToMove
        openChapter(chapter)
        click("deep-pov-${side.name.lowercase()}")
        openVariation(chapter, index)
        click("deep-variation-practice-$index")
        rule.waitUntil(60_000) { vm.uiState.value.trainer?.opening?.id == chapter.opening.id && !vm.uiState.value.lessonLoading }
        val trainer = vm.uiState.value.trainer!!
        val anchor = chapter.anchorPly(trainer.replay.pathId, v.nodeId)!!
        assertEquals(LessonMode.PRACTICE, trainer.mode)
        assertEquals(anchor, trainer.ply)
        assertEquals(position, trainer.position)
        assertFalse(trainer.isOpponentThinking)
        assertTrue(trainer.branchOffers.isEmpty())
        val move = trainer.replay.nextMove!!.move
        rule.runOnIdle { vm.trainerTap(move.from); vm.trainerTap(move.to); vm.restartTrainer() }
        assertEquals(anchor, vm.uiState.value.trainer!!.ply)
        assertEquals(position, vm.uiState.value.trainer!!.position)
        assertEquals(0, vm.uiState.value.trainer!!.mistakes)
    }

    @Test fun studyLineSelectionPrefersRegularEndingsThenReachThenLength() {
        ready()
        val chapter = repertoire
        for (variation in chapter.chapter.variations) {
            val candidates = chapter.linesByNode.getValue(variation.nodeId)
            val regular = candidates.filter { line ->
                val length = chapter.opening.variations.first { it.id == line }.steps.size
                chapter.nodeOnLine(line, length)?.verdict?.result != "TRANSPOSES"
            }
            val expected = (regular.ifEmpty { candidates }).maxWithOrNull(compareBy<String> { chapter.lineWeights[it] ?: 0.0 }
                .thenBy { chapter.opening.variations.first { v -> v.id == it }.steps.size })
            assertEquals(variation.name, expected, chapter.lineThrough(variation.nodeId))
        }
    }
}
