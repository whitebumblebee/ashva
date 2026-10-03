// SPDX-License-Identifier: Apache-2.0
package com.openinglab.content

import com.openinglab.shared.content.*
import com.openinglab.shared.data.*
import com.openinglab.shared.chess.*
import com.openinglab.shared.lesson.*
import com.openinglab.shared.model.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class TeachingCatalogTest {
    private fun source(): SourcedOpeningCatalog {
        val directory = Path.of("content/packs/lichess-openings-c67912be581f-import-v1")
        return SourcedOpeningCatalog(contentJson.decodeFromString(Files.readString(directory.resolve("manifest.json"))),
            Files.readAllLines(directory.resolve("openings.jsonl")).map { contentJson.decodeFromString<OpeningRecord>(it) })
    }
    @Test fun everyAuthoredStudyRouteIsLegalAndActuallyExplainedForBothColors() {
        assertTrue(StudyRoutes.routes.map { it.family }.distinct().size >= 30)
        val failures = mutableListOf<String>()
        for (route in StudyRoutes.routes) {
            try {
                val v = StudyRoutes.variation(route)
                var board = BoardPosition.starting()
                assertTrue(v.authoredContinuation)
                assertFalse(v.identifiesOpening)
                assertTrue(v.steps.size >= 15, v.name)
                assertTrue(v.whiteIdea.isNotBlank() && v.blackIdea.isNotBlank())
                for (step in v.steps) {
                    val move = ChessMove.fromUci(step.uci)
                    assertEquals(board.san(move), step.san, v.name)
                    assertTrue(step.explanation.isNotBlank() && step.principle.isNotBlank())
                    board = board.apply(move)
                }
            } catch (error: Exception) { failures += "${route.title}: ${error.message}" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
    @Test fun everySourceRouteIsTaughtWithoutMutatingTheLegacyCatalog() {
        val source = source()
        val originalIds = source.openings.map { it.id }.toSet()
        val catalog = TeachingCatalog(source)
        assertEquals(149, catalog.openings.size)
        assertEquals(3815, catalog.openings.sumOf { requireNotNull(it.teaching).sourceRoutes })
        assertTrue(catalog.openings.none { it.id in originalIds })
        assertTrue(catalog.openings.all { it.id.startsWith("course:v1:source:") })
        assertEquals(235, catalog.search("Ruy Lopez").single().teaching!!.sourceRoutes)
        assertTrue(catalog.search("Ruy Berlin").single().variations.size > 235)
        assertTrue(catalog.search("London").any { it.teaching!!.authoredRoutes >= 4 })
        for (opening in catalog.openings) {
            val old = source.openings.single { it.name == opening.name }
            val oldById = old.variations.associateBy { it.id }
            for (v in opening.variations.filterNot { it.authoredContinuation }) {
                assertEquals(oldById.getValue(v.id).steps.map { it.uci }, v.steps.map { it.uci })
                assertTrue(v.steps.all { !it.explanation.contains("not available") })
                assertTrue(v.whiteIdea != v.blackIdea)
            }
            assertTrue(old.variations.all { it.whiteIdea.contains("not available") })
        }
        val ruy = catalog.search("Ruy Lopez").single()
        val graph = LessonGraph.fromOpening(ruy)
        assertEquals(ruy.variations.size, graph.paths.size)
        assertEquals(StudyRoutes.routes.count { it.family == "Ruy Lopez" }, graph.paths.values.count { it.kind == LessonPathKind.AUTHORED })
        for (side in PieceColor.entries) {
            val replay = graph.start(side).jump(5)
            assertTrue(replay.branches().isNotEmpty())
            val branch = replay.branches().first()
            val diverged = replay.diverge(branch)
            assertEquals(replay.position.toFen(), diverged.returnToBranch().position.toFen())
        }
    }
    @Test fun cancellationIsObservedDuringCatalogWork() {
        var calls = 0
        assertFailsWith<IllegalStateException> { TeachingCatalog(source()) { if (++calls == 5) error("cancelled") } }
        assertEquals(5, calls)
    }
}
