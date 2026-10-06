// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.course

import com.openinglab.shared.lesson.LessonAnnotation
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.lesson.LessonPathKind
import com.openinglab.shared.lesson.LessonReplay
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.model.OpeningSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DeepCourseTest {
    private fun node(id: String, parent: String?, san: String, uci: String, role: String = "MAIN", weight: Double = 1.0, verdict: Verdict? = null) =
        CourseNode(id, parent, san, uci, role, "Title $san", "Text for $san.", "", "Generated · engine-checked", weight, verdict = verdict)

    private val equal = Verdict("EQUAL", 10, "White plan.", "Black plan.", "Generated · engine-checked")
    private val better = Verdict("WHITE_BETTER", 160, "Keep the extra pawn.", "Seek activity.", "Generated · engine-checked")

    private fun pack(nodes: List<CourseNode>, kind: String = "REPERTOIRE", game: CourseGame? = null) = DeepCoursePack(1, "tiny-course", 1, "Tiny", "WHITE",
        "test", "A tiny synthetic course.", CourseProvenance(listOf(CourseSource("s", "Synthetic", "https://example.invalid", "CC0-1.0", 2, "test")),
            "Engine", "depth 1", "test writer", "2026-10-05", "Test label policy.", "Test limitations."),
        listOf(CourseChapter("c", "Chapter", kind, "Intro.", 2, nodes, game = game)))

    // 1.e4 e5 2.Nf3 (main) ...Nc6 · 2...d6 (deviation) · 2...f6? (trap) 3.Nxe5
    private val tree = listOf(
        node("a", null, "e4", "e2e4"), node("b", "a", "e5", "e7e5"), node("c", "b", "Nf3", "g1f3"),
        node("d", "c", "Nc6", "b8c6", weight = 0.7, verdict = equal),
        node("e", "c", "d6", "d7d6", role = "DEVIATION", weight = 0.2, verdict = equal),
        node("f", "c", "f6", "f7f6", role = "TRAP", weight = 0.1),
        node("g", "f", "Nxe5", "f3e5", role = "PUNISH", weight = 0.1, verdict = better),
    )

    @Test fun validPackBecomesStudyableLinesWithRolesWeightsAndLabels() {
        val catalog = DeepCourseCatalog(listOf(pack(tree)))
        val view = catalog.chapters.single()
        assertEquals("course:deep-v1:tiny-course:v1:c", view.opening.id)
        assertEquals(3, view.opening.variations.size)
        assertEquals("Main line", view.opening.mainLine.name)
        assertEquals(listOf("MAIN", "DEVIATION", "TRAP"), view.opening.variations.map { view.roles.getValue(it.id) })
        assertEquals(1.0, view.lineWeights.values.sum(), 1e-9)
        val trap = view.opening.variations.first { view.roles[it.id] == "TRAP" }
        assertEquals("Common mistake · 2...f6", trap.name)
        assertTrue(trap.description.startsWith("White is better (+1.60)"))
        val graph = LessonGraph.fromOpening(view.opening)
        assertTrue(graph.paths.values.all { it.kind == LessonPathKind.COURSE_LINE })
        val replay = graph.start(PieceColor.WHITE, trap.id).last()
        assertEquals("Generated · engine-checked", replay.lastMove!!.annotation.label)
        assertEquals("Nxe5", replay.lastMove!!.san)
        // Branch offers exist where course lines diverge after 2.Nf3.
        assertTrue(graph.start(PieceColor.WHITE).jump(3).branches().isNotEmpty())
    }

    @Test fun illegalNotationMissingVerdictOrLabelFailsClosed() {
        assertFailsWith<IllegalArgumentException> { DeepCourseValidator.validate(pack(tree.map { if (it.id == "c") it.copy(san = "Nc3") else it })) }
        assertFailsWith<IllegalArgumentException> { DeepCourseValidator.validate(pack(tree.map { if (it.id == "d") it.copy(verdict = null) else it })) }
        assertFailsWith<IllegalArgumentException> { DeepCourseValidator.validate(pack(tree.map { if (it.id == "e") it.copy(label = "") else it })) }
        assertFailsWith<IllegalArgumentException> { DeepCourseValidator.validate(pack(tree.map { if (it.id == "e") it.copy(role = "INVENTED") else it })) }
        // Children before parents are rejected, so the tree is replayable in file order.
        assertFailsWith<IllegalArgumentException> { DeepCourseValidator.validate(pack(tree.reversed())) }
    }

    @Test fun gameChapterKeepsTheOriginalSeparateFromEngineBranches() {
        val game = CourseGame("A", "B", 2700, 2710, "Event", "2026.01.01", "1-0", "https://example.invalid/game", "CC-BY-SA-4.0")
        val nodes = listOf(node("a", null, "e4", "e2e4", "ORIGINAL"), node("b", "a", "e5", "e7e5", "ORIGINAL"),
            node("c", "b", "Nf3", "g1f3", "ORIGINAL"), node("x", "b", "Qh5", "d1h5", "BETTER", 0.0, better))
        val view = DeepCourseCatalog(listOf(pack(nodes, "GAME", game))).chapters.single()
        val graph = LessonGraph.fromOpening(view.opening)
        assertEquals(LessonPathKind.ORIGINAL_GAME, graph.paths.getValue(graph.originalPathId).kind)
        assertEquals("The game as played", view.opening.mainLine.name)
        assertTrue(graph.paths.values.any { it.kind == LessonPathKind.ANALYZED_VARIATION && it.name.startsWith("Better was") })
    }

    @Test fun unlabelledAnnotationTextIsUnchangedForSavedLessonFingerprints() {
        assertEquals("LessonAnnotation(title=t, explanation=e, principle=p, comments=[], nags=[])", LessonAnnotation("t", "e", "p").toString())
        assertEquals("LessonAnnotation(title=t, explanation=e, principle=p, comments=[], nags=[], label=L)", LessonAnnotation("t", "e", "p", label = "L").toString())
        assertEquals("LessonAnnotation(title=t, explanation=e, principle=p, comments=[], nags=[], players=2400+ 8 games)",
            LessonAnnotation("t", "e", "p", players = "2400+ 8 games").toString())
        assertEquals("LessonAnnotation(title=t, explanation=e, principle=p, comments=[], nags=[], label=L, players=2400+ 8 games)",
            LessonAnnotation("t", "e", "p", label = "L", players = "2400+ 8 games").toString())
    }

    @Test fun bothColourPackLetsTheLearnerChooseEitherSide() {
        val p = pack(tree).copy(side = "BOTH")
        val view = DeepCourseCatalog(listOf(p)).chapters.single()
        assertNull(p.learnerSide)
        assertEquals(OpeningSide.BOTH, view.opening.side)
        val graph = LessonGraph.fromOpening(view.opening)
        assertEquals(PieceColor.WHITE, graph.start(PieceColor.WHITE).playerSide)
        assertEquals(PieceColor.BLACK, graph.start(PieceColor.BLACK).playerSide)
    }

    @Test fun lineNamesUseOpeningNamesAndTheMoveThatBranchesAfterThem() {
        val named = tree.map { if (it.id in setOf("c", "d", "e", "f", "g")) it.copy(opening = "Open Game: King's Knight") else it }
        val view = DeepCourseCatalog(listOf(pack(named).copy(side = "BOTH"))).chapters.single()
        assertEquals("King's Knight", view.opening.mainLine.name)
        assertEquals("King's Knight · 2...d6", view.opening.variations.single { view.roles[it.id] == "DEVIATION" }.name)
        assertEquals("King's Knight · 2...f6", view.opening.variations.single { view.roles[it.id] == "TRAP" }.name)
    }

    @Test fun shortNamesAndAndroidLessonLineNamesKeepMiddleDotMoveLabels() {
        val name = "Open Game: King's Knight, Main Line · 2...d6"
        assertEquals("King's Knight, Main Line · 2...d6", DeepCourseCatalog.shortName(name))
        assertEquals("Berlin Defense · 4...Bc5", DeepCourseCatalog.shortName("Berlin Defense · 4...Bc5"))
        val named = tree.map { if (it.id == "e") it.copy(opening = name) else it }
        val view = DeepCourseCatalog(listOf(pack(named).copy(side = "BOTH"))).chapters.single()
        val branch = view.opening.variations.single { view.roles[it.id] == "DEVIATION" }
        assertEquals("King's Knight, Main Line · 2...d6", branch.name)
        assertEquals(branch.name, LessonGraph.fromOpening(view.opening).start(PieceColor.WHITE, branch.id).path.name)
    }

    private val example = CourseExample("A", "B", 2700, 2710, "Event", "2026.01.01", "1-0", "https://example.invalid/game",
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6"), 3)
    private fun variation(nodeId: String = "c") = CourseVariation("King's Knight", nodeId, listOf("e4", "e5", "Nf3"), 20, 10, 5, 5,
        mapOf("2600+" to 20), "Intro.", "Generated · game statistics", CourseSideIdeas(10, "White ideas.", "Game statistics", listOf(example)),
        CourseSideIdeas(5, "Black ideas.", "Game statistics", emptyList()))
    private fun withVariation(v: CourseVariation = variation()): DeepCoursePack {
        val p = pack(tree).copy(side = "BOTH")
        return p.copy(chapters = listOf(p.chapters.single().copy(variations = listOf(v))))
    }

    @Test fun exampleIdCreatesAndCachesAnOriginalGameWithExactMoves() {
        val catalog = DeepCourseCatalog(listOf(withVariation()))
        val view = catalog.chapters.single()
        assertSame(view.opening, catalog.getOpening(view.opening.id))
        val id = catalog.exampleId(view, 0, true, 0)
        val opening = assertNotNull(catalog.getOpening(id))
        assertEquals(OpeningSide.BOTH, opening.side)
        val graph = LessonGraph.fromOpening(opening)
        val replay = graph.start(PieceColor.WHITE)
        assertEquals(LessonPathKind.ORIGINAL_GAME, replay.path.kind)
        assertEquals(example.uci, replay.moves.map { it.move.uci })
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6"), replay.moves.map { it.san })
        assertEquals("The variation starts here", replay.moves[2].annotation.title)
        assertSame(opening, catalog.getOpening(id))
        assertNull(catalog.getOpening(catalog.exampleId(view, 0, false, 0)))
        assertNull(catalog.getOpening("${view.opening.id}:ex:0:invalid:0"))
        assertNull(catalog.getOpening(catalog.exampleId(view, 1, true, 0)))
        assertNull(catalog.getOpening("missing"))
    }

    @Test fun lineThroughSelectsTheMostReachedLineContainingTheNode() {
        val weighted = tree.map { when (it.id) { "d" -> it.copy(weight = 0.1); "e" -> it.copy(weight = 0.8); else -> it } }
        val view = DeepCourseCatalog(listOf(pack(weighted))).chapters.single()
        val chosen = assertNotNull(view.lineThrough("c"))
        assertEquals("d6", view.opening.variations.single { it.id == chosen }.steps.last().san)
        assertTrue("c" in view.lineNodes.getValue(chosen))
        assertEquals(view.opening.mainLine.id, view.lineThrough("d"))
        assertEquals("Nxe5", view.opening.variations.single { it.id == view.lineThrough("f") }.steps.last().san)
        assertNull(view.lineThrough("missing"))
    }

    @Test fun playersTextOmitsZeroBandsAndReachesTheLessonAnnotation() {
        val n = tree.first().copy(evidence = NodeEvidence(bands = linkedMapOf("2600+" to 8, "2400–2599" to 0, "2200–2399" to 12)))
        val text = "Chosen in: 2600+ 8 · 2200–2399 12 games"
        assertEquals(text, DeepCourseCatalog.players(n))
        assertEquals("", DeepCourseCatalog.players(tree.first()))
        assertEquals("", DeepCourseCatalog.players(n.copy(evidence = NodeEvidence(bands = mapOf("2600+" to 0)))))
        val opening = DeepCourseCatalog(listOf(pack(listOf(n) + tree.drop(1)))).chapters.single().opening
        assertEquals(text, opening.mainLine.steps.first().players)
        assertEquals(text, LessonGraph.fromOpening(opening).start(PieceColor.WHITE).next().lastMove!!.annotation.players)
    }

    @Test fun variationWithMissingNodeIsRejected() {
        DeepCourseValidator.validate(withVariation())
        assertFailsWith<IllegalArgumentException> { DeepCourseValidator.validate(withVariation(variation("missing"))) }
    }

    @Test fun packJsonRoundTripsThroughTheStrictParser() {
        val text = kotlinx.serialization.json.Json.encodeToString(DeepCoursePack.serializer(), pack(tree))
        assertEquals(pack(tree), DeepCourseCatalog.parse(text))
        assertFailsWith<Exception> { DeepCourseCatalog.parse(text.replaceFirst("\"schema\":1", "\"schema\":1,\"unexpected\":true")) }
    }
    @Test fun olderSideIdeasKeepDefaultsAndStructuredIdeasRoundTrip() {
        val codec = kotlinx.serialization.json.Json
        val old = codec.decodeFromString(CourseSideIdeas.serializer(), """{"wins":10,"text":"Plan and stats.","label":"Generated","examples":[]}""")
        assertEquals("", old.plan)
        assertTrue(old.patterns.isEmpty())
        val structured = old.copy(plan = "Keep pieces active.", patterns = listOf(CoursePattern("Nxf5", .42, .18)))
        assertEquals(structured, codec.decodeFromString(CourseSideIdeas.serializer(), codec.encodeToString(CourseSideIdeas.serializer(), structured)))
    }

    @Test fun validatorRejectsInvalidStructuredPatternsAndOversizedPlans() {
        fun check(ideas: CourseSideIdeas) = DeepCourseValidator.validate(withVariation(variation().copy(white = ideas)))
        val ideas = variation().white.copy(plan = "Develop.", patterns = listOf(CoursePattern("Nxf5", .42, .18)))
        check(ideas)
        for (pattern in listOf(CoursePattern("", .42, .18), CoursePattern("Nxf5", Double.NaN, .18),
            CoursePattern("Nxf5", -.1, .18), CoursePattern("Nxf5", .42, 1.1))) {
            assertFailsWith<IllegalArgumentException> { check(ideas.copy(patterns = listOf(pattern))) }
        }
        assertFailsWith<IllegalArgumentException> { check(ideas.copy(plan = "x".repeat(DeepCourseValidator.MAX_TEXT + 1))) }
    }

    @Test fun weightedVariationPracticeOnlyPicksDescendantsAndFallsBackForZeroWeights() {
        val view = DeepCourseCatalog(listOf(pack(tree))).chapters.single()
        repeat(100) { seed ->
            val line = assertNotNull(view.weightedLineThrough("f", kotlin.random.Random(seed)))
            assertTrue("f" in view.lineNodes.getValue(line))
        }
        assertNull(view.weightedLineThrough("missing"))
        val zero = view.copy(lineWeights = view.lineWeights.mapValues { 0.0 })
        assertNotNull(zero.weightedLineThrough("c", kotlin.random.Random(1)))
    }

    @Test fun treeUsesTheLongestStrictMovePrefixAndShortensChildNames() {
        val root = variation().copy(name = "Berlin Defense", path = listOf("e4"))
        val anti = root.copy(name = "Berlin Defense, Anti-Berlin Variation", path = listOf("e4", "e5"))
        val move = anti.copy(name = "Berlin Defense, Anti-Berlin Variation · 4...Bc5", path = listOf("e4", "e5", "Nf3"))
        val hermet = root.copy(name = "Berlin Defense, l'Hermet Variation", path = listOf("e4", "c5"))
        val wall = hermet.copy(name = "Berlin Defense, l'Hermet Variation, Berlin Wall Defense", path = listOf("e4", "c5", "Nf3"))
        val duplicatePath = root.copy(name = "Another name", path = move.path)
        val tree = CourseVariationTree(listOf(move, root, anti, hermet, wall, duplicatePath))
        assertEquals(listOf(2, null, 1, 1, 3, 2), tree.parents)
        assertEquals(listOf(1), tree.children(null))
        assertEquals("4...Bc5", tree.shortName(0))
        assertEquals("Berlin Wall Defense", tree.shortName(4))
        assertEquals("Another name", tree.shortName(5))
    }

    @Test fun chapterDisplayTitleDoesNotChangeItsLessonIdentity() {
        val p = pack(tree)
        val chapter = p.chapters.single().copy(title = "Tiny: Closed Ruy Lopez: main systems")
        assertEquals("Closed Ruy Lopez: main systems", chapter.displayTitle(p))
        assertEquals("Tiny: Closed Ruy Lopez: main systems", chapter.title)
    }

    @Test fun summariesDoNotReplayOrPresentUnopenedChaptersAndBadMovesFailOnDemand() {
        val good = pack(tree).chapters.single()
        val bad = good.copy(id = "bad", nodes = tree.map { if (it.id == "c") it.copy(san = "Nc3") else it })
        val p = pack(tree).copy(chapters = listOf(good, bad))
        val catalog = DeepCourseCatalog(listOf(p), validateOnDemand = true)
        assertEquals(listOf(3, 3), catalog.summaries.map { it.lineCount })
        val goodId = catalog.summaries.first().openingId
        val badId = catalog.summaries.last().openingId
        assertNull(catalog.preparedChapter(goodId))
        assertNull(catalog.preparedOpening(goodId))
        val chapter = assertNotNull(catalog.chapter(goodId))
        assertSame(chapter, catalog.chapter(goodId))
        assertNull(catalog.preparedChapter(badId))
        assertFailsWith<IllegalArgumentException> { catalog.chapter(badId) }
        assertNull(catalog.preparedChapter(badId))
        assertFailsWith<IllegalArgumentException> { DeepCourseCatalog(listOf(p)) }
    }

    @Test fun checkedGraphPreservesExactPathsPositionsBranchesAndColdSnapshots() {
        val view = DeepCourseCatalog(listOf(pack(tree)), validateOnDemand = true).chapters.single()
        val legacy = LessonGraph.fromOpening(view.opening)
        val checked = view.lessonGraph()
        assertEquals(legacy.paths, checked.paths)
        assertEquals(legacy.nodes, checked.nodes)
        val atBranch = legacy.start(PieceColor.BLACK).jump(3)
        val branched = atBranch.diverge(atBranch.branches().first()).last()
        val restored = LessonReplay.restore(checked, branched.snapshot())
        assertEquals(branched.position, restored.position)
        assertEquals(branched.moves, restored.moves)
        assertEquals(branched.returnToBranch().position, restored.returnToBranch().position)
        checked.paths.values.forEach { assertSame(view.nodePositions.getValue("c"), it.positions[3]) }
    }

    @Test fun replayVisitsEveryNodeOnceAndCancelledPreparationPublishesNothing() {
        var visits = 0
        val boards = DeepCourseValidator.replayChapter(pack(tree).chapters.single(), checkpoint = { visits++ })
        assertEquals(tree.size, visits)
        assertEquals(tree.size, boards.size)
        val catalog = DeepCourseCatalog(listOf(pack(tree)), validateOnDemand = true)
        val id = catalog.summaries.single().openingId
        assertFailsWith<IllegalStateException> { catalog.chapter(id) { error("cancel checkpoint") } }
        assertNull(catalog.preparedChapter(id))
        assertNotNull(catalog.chapter(id))
    }

    @Test fun checkedNodeBoardsKeepRepetitionAndClocksAtAnExactHistoryPrefix() {
        val cycle = listOf(node("a", null, "Nf3", "g1f3"), node("b", "a", "Nf6", "g8f6"),
            node("c", "b", "Ng1", "f3g1"), node("d", "c", "Ng8", "f6g8"),
            node("e", "d", "Nf3", "g1f3", verdict = equal))
        val view = DeepCourseCatalog(listOf(pack(cycle)), validateOnDemand = true).chapters.single()
        val legacy = LessonGraph.fromOpening(view.opening).start(PieceColor.WHITE).last()
        val checked = view.lessonGraph().start(PieceColor.WHITE).last()
        assertEquals(legacy.position, checked.position)
        assertEquals(2, checked.position.repetitionCount)
        assertEquals(5, checked.position.halfmoveClock)
    }

    @Test fun lineThroughPrefersRegularEndingsBeforeCumulativeReach() {
        val nodes = tree.map { if (it.id == "d") it.copy(weight = .99, verdict = equal.copy(result = "TRANSPOSES")) else it }
        val view = DeepCourseCatalog(listOf(pack(nodes))).chapters.single()
        val chosen = assertNotNull(view.lineThrough("c"))
        assertEquals("e", view.lineNodes.getValue(chosen).last())
        assertEquals(.2, view.lineWeights.getValue(chosen))
    }

    @Test fun lineThroughBreaksReachTiesWithTheLongerLine() {
        val view = DeepCourseCatalog(listOf(pack(tree))).chapters.single()
        val tied = view.copy(lineWeights = view.lineWeights.mapValues { .2 })
        assertEquals("g", tied.lineNodes.getValue(assertNotNull(tied.lineThrough("c"))).last())
    }

    @Test fun lineThroughFallsBackToMostReachedTranspositionAndHandlesMissingAnchors() {
        val nodes = tree.map { n -> n.copy(verdict = n.verdict?.copy(result = "TRANSPOSES")) }
        val view = DeepCourseCatalog(listOf(pack(nodes))).chapters.single()
        assertEquals("d", view.lineNodes.getValue(assertNotNull(view.lineThrough("c"))).last())
        assertNull(view.lineThrough("missing"))
        val tied = view.copy(lineWeights = view.lineWeights.mapValues { .2 })
        assertEquals("g", tied.lineNodes.getValue(assertNotNull(tied.lineThrough("c"))).last())
    }

    @Test fun anchorPlyIsAfterTheDefiningMoveAndEarlierMovesRemainSteppable() {
        val view = DeepCourseCatalog(listOf(pack(tree))).chapters.single()
        val line = assertNotNull(view.lineThrough("f"))
        val anchor = assertNotNull(view.anchorPly(line, "f"))
        assertEquals(4, anchor)
        val replay = view.lessonGraph().start(PieceColor.WHITE, line).jump(anchor)
        assertEquals(view.nodePositions.getValue("f"), replay.position)
        assertEquals(view.nodePositions.getValue("c"), replay.previous().position)
        assertTrue(replay.first().atStart)
        assertNull(view.anchorPly(line, "missing"))
        assertNull(view.anchorPly("missing", "f"))
    }

    @Test fun movePopularityUsesShareAtThePositionEvenWithTinyCumulativeReach() {
        val counts = mapOf("d" to 2217, "e" to 665, "f" to 118)
        val nodes = tree.map { it.copy(weight = .00001, evidence = NodeEvidence(masterGames = counts[it.id] ?: 0)) }
        val view = DeepCourseCatalog(listOf(pack(nodes))).chapters.single()
        assertEquals("Main move", view.movePopularity("d"))
        assertEquals("Popular", view.movePopularity("e"))
        assertNull(view.movePopularity("f"))
        assertNull(view.movePopularity("missing"))
    }

    @Test fun movePopularityUsesExactThresholdsAndClubCountsOnlyWithoutMasterCounts() {
        val counts = mapOf("d" to 50, "e" to 15, "f" to 35)
        val nodes = tree.map { it.copy(evidence = NodeEvidence(clubGames = counts[it.id] ?: 0)) }
        val view = DeepCourseCatalog(listOf(pack(nodes))).chapters.single()
        assertEquals("Main move", view.movePopularity("d"))
        assertEquals("Popular", view.movePopularity("e"))
        assertEquals("Popular", view.movePopularity("f"))
        val withMasters = nodes.map { it.copy(evidence = it.evidence!!.copy(masterGames = if (it.id == "e") 1 else 0)) }
        val masterView = DeepCourseCatalog(listOf(pack(withMasters))).chapters.single()
        assertNull(masterView.movePopularity("d"))
        assertEquals("Main move", masterView.movePopularity("e"))
    }

    @Test fun movePopularityNeverInfersFrequencyFromCumulativeWeightsWithoutCounts() {
        val view = DeepCourseCatalog(listOf(pack(tree))).chapters.single()
        assertTrue(tree.all { view.movePopularity(it.id) == null })
    }

    @Test fun friendlyEndingsUseOneDecimalAndWhitePerspectiveWithoutReachText() {
        assertEquals("Ends equal (+0.1)", friendlyLineEnding(equal.copy(evalCp = 12)))
        assertEquals("White ends better (+1.3)", friendlyLineEnding(better.copy(evalCp = 130)))
        assertEquals("Black ends better (-1.3)", friendlyLineEnding(equal.copy(result = "BLACK_BETTER", evalCp = -130)))
        assertEquals("Ends equal (0.0)", friendlyLineEnding(equal.copy(evalCp = -1)))
        assertEquals("White ends better", friendlyLineEnding(better.copy(evalCp = null)))
        val view = DeepCourseCatalog(listOf(pack(tree))).chapters.single()
        assertEquals("Ends equal (+0.1)", view.lineEndingSummary(view.opening.mainLine.id))
        assertTrue(view.opening.mainLine.description.contains("Reached in about")) // Retained fingerprint text stays unchanged.
    }

    @Test fun transpositionEndingsNameOnlyTheirCheckedDestination() {
        val verdict = equal.copy(result = "TRANSPOSES", whitePlan = "This position also arises by another move order (Ruy Lopez: Berlin Defence). Its continuations are taught there.")
        assertEquals("Joins Berlin Defence", friendlyLineEnding(verdict))
        assertEquals("Joins another line", friendlyLineEnding(verdict.copy(whitePlan = "No named destination.")))
    }

    @Test fun unclearMissingAndGameResultEndingsStayShort() {
        assertEquals("Unclear", friendlyLineEnding(null))
        assertEquals("Unclear", friendlyLineEnding(equal.copy(result = "UNCLEAR")))
        assertEquals("Unclear", friendlyLineEnding(equal.copy(result = "GAME_RESULT")))
    }

    @Test fun optionalPracticeAnchorKeepsOldBookmarksReadableAndSurvivesRoundTrip() {
        val view = DeepCourseCatalog(listOf(pack(tree))).chapters.single()
        val old = com.openinglab.shared.storage.LessonBookmark(view.opening.id, "retained-version",
            view.lessonGraph().start(PieceColor.WHITE).snapshot(), "STUDY")
        val codec = kotlinx.serialization.json.Json
        val serializer = com.openinglab.shared.storage.LessonBookmark.serializer()
        val oldJson = codec.encodeToString(serializer, old)
        assertTrue(!oldJson.contains("practiceStartPly"))
        assertEquals(0, codec.decodeFromString(serializer, oldJson).practiceStartPly)
        val anchored = old.copy(practiceStartPly = 3)
        assertEquals(anchored, codec.decodeFromString(serializer, codec.encodeToString(serializer, anchored)))
    }

}
