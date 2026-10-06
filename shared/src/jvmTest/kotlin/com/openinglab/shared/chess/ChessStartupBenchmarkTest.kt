// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.chess

import com.openinglab.shared.content.*
import com.openinglab.shared.course.*
import com.openinglab.shared.data.*
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.storage.contentSha256
import com.openinglab.shared.tactics.*
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue

/** Reproducible JVM diagnostic over real bundled inputs. Run with --tests '*ChessStartupBenchmarkTest'. */
class ChessStartupBenchmarkTest {
    private fun <T> timed(name: String, block: () -> T): T {
        val bean = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        val cpu = bean.processCpuTime
        val start = System.nanoTime()
        return block().also { println("CHESS_BENCH $name wall_ms=%.3f cpu_ms=%.3f".format(
            (System.nanoTime() - start) / 1e6, (bean.processCpuTime - cpu) / 1e6)) }
    }
    @Test fun bundledStartupAndChessCore() {
        val directory = File("content/packs/lichess-openings-c67912be581f-import-v1")
        assumeTrue(directory.isDirectory)
        val source = timed("source_catalog") { SourcedOpeningCatalog(
            Json.decodeFromString(directory.resolve("manifest.json").readText()),
            directory.resolve("openings.jsonl").readLines().map { Json.decodeFromString<OpeningRecord>(it) }) }
        val teaching = timed("teaching_constructor") { TeachingCatalog(source, onDemand = true) }
        timed("teaching_all_routes") { assertEquals(149, teaching.openings.size) }
        val positions = listOf(BoardPosition.starting(),
            BoardPosition.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"),
            BoardPosition.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"))
        repeat(1000) { positions[it % positions.size].legalMoves() }
        timed("legal_moves_10000_positions") {
            var count = 0
            repeat(10_000) { count += positions[it % positions.size].legalMoves().size }
            assertEquals(273326, count)
        }
        timed("san_replay_all_bundled_routes") {
            var plies = 0
            source.records.forEach { route ->
                var board = BoardPosition.starting()
                route.san.forEachIndexed { i, san ->
                    val transition = board.parseSanAndPlay(san)
                    assertEquals(route.uci[i], transition.move.uci)
                    board = transition.position; plies++
                }
            }
            println("CHESS_BENCH source_plies=$plies")
        }
        // All presentation fields, including order, annotations and both-color plans, must remain identical.
        val teachingText = teaching.openings.joinToString("\n") { it.toString() }
        assertEquals("75bd02824b0bda934fa15fd2092a66ae4c68771f148af64254598316e9c206a2", contentSha256(teachingText.encodeToByteArray()))
        println("CHESS_BENCH teaching_fingerprint=${contentSha256(teachingText.encodeToByteArray())}")
        val tacticsFile = File("content/tactics/v1/tactics.json")
        if (tacticsFile.isFile) {
            val tactics = timed("tactics_decode") { Json.decodeFromString<TacticsPack>(tacticsFile.readText()) }
            timed("tactics_full_validation") { TacticsPackValidator.validate(tactics) }
        }
        val courseFile = File("content/courses/ruy-lopez/v1/course.json")
        if (courseFile.isFile) {
            val course = timed("deep_course_decode") { DeepCourseCatalog.parse(courseFile.readText()) }
            timed("deep_course_full_validation") { DeepCourseValidator.validate(course) }
        }
    }
    private class FileMarkers(private val file: File, private val identity: PackValidationIdentity) : PackValidationCache {
        private val values = if (file.isFile) file.readLines().toMutableSet() else mutableSetOf()
        override fun isValidated(section: String) = identity.key(section) in values
        override fun markValidated(section: String) { values.add(identity.key(section)); file.writeText(values.joinToString("\n")) }
    }

    @Test fun secondLaunchHomeWorkWithPersistedMarkers() {
        val sourceDir = File("content/packs/lichess-openings-c67912be581f-import-v1")
        val courseFile = File("content/courses/ruy-lopez/v1/course.json")
        val tacticsFile = File("content/tactics/v1/tactics.json")
        assumeTrue(sourceDir.isDirectory && courseFile.isFile && tacticsFile.isFile)
        val courseHash = "7437b57ef044c4d3a42cbad45044095db291cf72b65cf803175b6c8b2243fa58"
        val tacticsHash = "4cf96253712fbd04e551df12a127d854efccde9e5a0edc9695e10e109ed0e613"
        val sourceHash = "40d2ab2920dafa5c60ce163695c8e0e3fa31cf1cf922a285461aeafcb1f55dc3"
        val directory = Files.createTempDirectory("ashva-validation-markers-").toFile()
        try {
            val courseIdentity = PackValidationIdentity(courseHash, 19, DeepCourseValidator.VERSION)
            val tacticsIdentity = PackValidationIdentity(tacticsHash, 19, TacticsPackValidator.VERSION)
            val courseMarker = FileMarkers(File(directory, "courses"), courseIdentity)
            val tacticsMarker = FileMarkers(File(directory, "tactics"), tacticsIdentity)
            fun loadHome(): DeepCourseCatalog {
                val sourceBytes = sourceDir.resolve("manifest.json").readBytes()
                assertEquals(sourceHash, contentSha256(sourceBytes))
                val source = SourcedOpeningCatalog(Json.decodeFromString(sourceBytes.decodeToString()),
                    sourceDir.resolve("openings.jsonl").readLines().map { Json.decodeFromString<OpeningRecord>(it) })
                val teaching = TeachingCatalog(source, onDemand = true)
                assertEquals(149, teaching.summaries.size)
                assertEquals(3815, teaching.summaries.sumOf { it.teaching!!.sourceRoutes })
                val courseBytes = courseFile.readBytes()
                assertEquals(courseHash, contentSha256(courseBytes))
                val cachedCourse = DeepCourseCatalog(listOf(DeepCourseCatalog.parse(courseBytes.decodeToString())),
                    validateOnDemand = true, validationCache = FileMarkers(File(directory, "courses"), courseIdentity))
                assertEquals(898, cachedCourse.summaries.sumOf { it.lineCount })
                val tacticsBytes = tacticsFile.readBytes()
                assertEquals(tacticsHash, contentSha256(tacticsBytes))
                val tactics = TacticsPackValidator.decode(tacticsBytes.decodeToString())
                TacticsPackValidator.validateStructure(tactics)
                tactics.byId
                return cachedCourse
            }
            // Home does metadata/checksum/decode work; missing proofs are filled after its first frame.
            val first = timed("home_first_launch_without_markers") { loadHome() }
            assertEquals(false, courseMarker.isValidated("pack"))
            assertEquals(false, tacticsMarker.isValidated("pack"))
            // Install/first-run work finishes successfully before proofs are written.
            first.validateRemaining()
            val fullCourse = first.packs.single()
            TacticsPackValidator.validateOnce(TacticsPackValidator.decode(tacticsFile.readText()), tacticsMarker)
            assertEquals(true, FileMarkers(File(directory, "courses"), courseIdentity).isValidated("pack"))
            assertEquals(true, tacticsMarker.isValidated("pack"))
            val catalog = timed("home_second_launch_with_markers") {
                val cached = loadHome()
                var replayed = 0
                cached.validateRemaining { replayed++ }
                assertEquals(0, replayed)
                cached
            }
            val id = catalog.summaries.first { it.chapter.id == "morphy-other" }.openingId
            val cached = timed("cached_largest_chapter_rebuild_and_graph") { catalog.chapter(id)!!.lessonGraph() }
            val full = DeepCourseCatalog(listOf(fullCourse), validateOnDemand = true).chapter(id)!!.lessonGraph()
            assertEquals(full.paths, cached.paths)
            assertEquals(full.nodes, cached.nodes)
        } finally { directory.deleteRecursively() }
    }

}
