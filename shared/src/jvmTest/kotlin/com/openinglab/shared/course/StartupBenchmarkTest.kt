// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.course

import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.practice.*
import com.openinglab.shared.storage.contentSha256
import com.openinglab.shared.storage.RoomLearningStore
import com.openinglab.shared.storage.StudyActivityEntity
import com.openinglab.shared.storage.createJvmLearningDatabase
import com.openinglab.shared.tactics.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue

/** Real, local packs only. Missing packs skip this diagnostic; no device or network required. */
class StartupBenchmarkTest {
    private fun <T> timed(stage: String, block: () -> T): T {
        val start = System.nanoTime()
        return block().also { println("STARTUP $stage: %.3f ms".format((System.nanoTime() - start) / 1e6)) }
    }

    private fun fingerprint(graph: LessonGraph): String {
        val description = graph.paths.values.joinToString("\n") {
            "${it.id}|${it.description}|${it.whiteIdea}|${it.blackIdea}|" + it.moves.joinToString(";") { move ->
                "${move.move.uci}|${move.san}|${move.annotation}"
            }
        }
        return "${description.hashCode()} ${contentSha256(description.encodeToByteArray())}"
    }

    // Captured from the pre-optimization graphs of the checksum-pinned, real bundled pack.
    // Lesson identities of the bundled pack (0.18.1: main lines follow the most-played moves and joined move orders continue).
    private val legacyFingerprints = mapOf(
        "berlin" to "1223822850 4ffa24faae1299d7e34a1bcb21c04d253e17ef91cb055a73847833618c864ec1",
        "exchange" to "342598485 84a647a2b1e03668fdccff2cebd12316e7576260a6c14b99f7ccfef1c391a122",
        "open" to "-779157893 12df92acc1d3d6f9a06eac0db8387a96145813954f9313d88cc70274c06a8341",
        "closed" to "1237165960 631752bdf88681064a8bc9e9dd7da2d01ba40e824b53a4cf2db8bcd310279a54",
        "marshall" to "134749387 b5563bada963ef2b23c3d5a274219fb3c51a6467a992094f4e0fa3ba770b9de8",
        "morphy-other" to "1727388754 e56ff4fbcd4f3632b4223ee7a96b1144942ea6ed22dca6754a310733eca594a8",
        "schliemann" to "1708033370 b7ec4fc3142919a7e1e6ed1cd4d0dd76732d08caca27def1c338da808f29190f",
        "classical" to "-716694711 f1dd609347f744a0a353ded10fd527c978b9bd54f3cf701e2653d74b7aab2967",
        "steinitz" to "1777915476 04bfdd5630704cd2c790ac15d7647e139fbc2b5081dc0187a27bf0978ad4f839",
        "other" to "-579380348 d7c639641ff3a190cbcb61bd84819fa3302add306e15dfcdb1a556a7dbc3cf49",
        "firouzja-carlsen-2020" to "-373417485 004787df8958c4b98b25b84d24c30d5ea55a630595ad4e30d8c2e808d3c9e682",
    )

    @Test fun realPackStartupStages() {
        val file = File("content/courses/ruy-lopez/v1/course.json")
        assumeTrue("local course pack missing", file.isFile)
        val homeStart = System.nanoTime()
        val bytes = timed("course read") { file.readBytes() }
        val packSha = contentSha256(bytes)
        timed("course checksum") { assertEquals("7437b57ef044c4d3a42cbad45044095db291cf72b65cf803175b6c8b2243fa58", packSha) }
        val pack = timed("JSON decode") { DeepCourseCatalog.parse(bytes.decodeToString()) }
        val catalog = timed("Home catalog/summaries (structure only)") { DeepCourseCatalog(listOf(pack), validateOnDemand = true) }
        timed("Home line count/progress (empty history)") {
            val activity = LearnerActivity()
            catalog.summaries.sumOf { activity.studiedLineCounts[it.openingId] ?: 0 }
            assertEquals(898, catalog.summaries.sumOf { it.lineCount })
            val calendar = StudyCalendar({ 1_000L }) { it / 86_400_000 }
            calendar.week(emptyList())
            routineProgress(calendar, activity, emptyList(), RoutineSettings())
        }
        println("STARTUP HOME TOTAL: %.3f ms".format((System.nanoTime() - homeStart) / 1e6))
        val largestStart = System.nanoTime()
        val largest = pack.chapters.first { it.id == "morphy-other" }
        val largestView = timed("morphy-other on-demand validation + present + indexes/tree") {
            catalog.chapter(DeepCourseCatalog.openingId(pack, largest))!!
        }
        val largestGraph = timed("morphy-other checked LessonGraph") { largestView.lessonGraph() }
        timed("morphy-other fingerprint text + hash") { fingerprint(largestGraph) }
        println("STARTUP LARGEST CHAPTER TOTAL: %.3f ms".format((System.nanoTime() - largestStart) / 1e6))
        val checked = timed("DeepCourseValidator.validate") { DeepCourseValidator.validate(pack) }
        for (chapter in pack.chapters) {
            timed("legal replay/${chapter.id}") { DeepCourseValidator.replayChapter(chapter) }
            val view = timed("present/${chapter.id} (names, Opening, lineNodes, weights, indexes/tree)") {
                DeepCourseCatalog.present(pack, chapter, checked.getValue(chapter.id))
            }
            val graph = timed("LessonGraph/${chapter.id}") { view.lessonGraph() }
            val description = timed("fingerprint text/${chapter.id}") { graph.paths.values.joinToString("\n") {
                "${it.id}|${it.description}|${it.whiteIdea}|${it.blackIdea}|" + it.moves.joinToString(";") { move ->
                    "${move.move.uci}|${move.san}|${move.annotation}"
                }
            } }
            val fingerprint = timed("fingerprint hash/${chapter.id}") { contentSha256(description.encodeToByteArray()) }
            println("FINGERPRINT ${chapter.id} ${description.hashCode()} $fingerprint")
            assertEquals(legacyFingerprints.getValue(chapter.id), "${description.hashCode()} $fingerprint", "Saved lesson identity changed")
            timed("variation tree/${chapter.id}") { CourseVariationTree(chapter.variations) }
            timed("lineThrough all rows/${chapter.id}") { chapter.variations.forEach { view.lineThrough(it.nodeId) } }
        }
        timed("routine/activity queries (empty history)") {
            val calendar = StudyCalendar({ 1_000L }) { it / 86_400_000 }
            calendar.week(emptyList())
            routineProgress(calendar, LearnerActivity(), emptyList(), RoutineSettings())
        }
        val tactics = File("content/tactics/v1/tactics.json")
        assumeTrue("local tactics pack missing", tactics.isFile)
        val tacticsBytes = timed("tactics read") { tactics.readBytes() }
        timed("tactics checksum") { assertEquals("4cf96253712fbd04e551df12a127d854efccde9e5a0edc9695e10e109ed0e613", contentSha256(tacticsBytes)) }
        val puzzles = timed("TacticsPack decode") { Json.decodeFromString<TacticsPack>(tacticsBytes.decodeToString()) }
        timed("TacticsPack validation") { TacticsPackValidator.validate(puzzles) }
        timed("tactics index") { puzzles.byId }
        timed("custom pool filter") { puzzles.puzzles.count { it.rating in 800..2600 } }
        timed("custom pool select") { CustomSetSpec("Benchmark", 800, 2600, size = 50, seed = 1).select(puzzles) }
    }

    @Test fun routineAndActivityQueries() {
        val directory = Files.createTempDirectory("ashva-startup-benchmark-")
        val path = directory.resolve("fixture.db").toString()
        try {
            val fixture = createJvmLearningDatabase(path)
            try { runBlocking {
                repeat(3_000) { i -> fixture.learningDao().openingActivity(StudyActivityEntity("event-$i", "lesson-${i % 11}",
                    "line-${i % 898}", if (i % 2 == 0) StudyActivity.STUDY else StudyActivity.PRACTICE, i.toLong())) }
            } } finally { fixture.close() }
            val cold = createJvmLearningDatabase(path)
            try {
                val store = RoomLearningStore(cold)
                val activity = timed("Room cold learnerActivity queries (3,000 events)") { runBlocking { store.learnerActivity(10_000).first() } }
                timed("progress index (3,000 events)") { activity.studiedLineCounts; activity.practisedLineCount }
                val ids = (0..10).map { "lesson-$it" }.toSet()
                var legacyCount = 0
                timed("legacy progress 1,000 recompositions") { repeat(1_000) {
                    legacyCount = activity.openings.filter { it.kind == StudyActivity.STUDY && it.lessonId in ids }
                        .map { it.lessonId to it.pathId }.distinct().size
                } }
                var indexedCount = 0
                timed("indexed progress 1,000 recompositions") { repeat(1_000) {
                    indexedCount = ids.sumOf { activity.studiedLineCounts[it] ?: 0 }
                } }
                assertEquals(legacyCount, indexedCount)
                timed("routine/week (3,000 events)") {
                    val calendar = StudyCalendar({ 10_000L }) { it / 86_400_000 }
                    calendar.week(activity.timestamps)
                    routineProgress(calendar, activity, emptyList(), RoutineSettings())
                }
                val history = TacticsHistory(cycles = (0..99).map { TacticsCycle("set-${it % 10}", it / 10 + 1, it.toLong()) },
                    attempts = (0..2999).map { TacticsAttempt("set-${it % 10}", it / 300 + 1, "p$it", it / 10 % 30, true, 1, it.toLong()) })
                val legacy = timed("legacy tactics Home history queries") {
                    history.cycles.maxByOrNull { c -> maxOf(c.startedAt, history.attempts.filter { it.setId == c.setId }.maxOfOrNull { it.at } ?: 0) }
                }
                timed("indexed tactics Home history queries") {
                    assertEquals(legacy, history.latestCycle)
                    history.cyclesBySet; history.attemptsByCycle
                }
            } finally { cold.close() }
        } finally { Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
