// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.data

import com.openinglab.shared.chess.*
import com.openinglab.shared.content.*
import com.openinglab.shared.lesson.*
import com.openinglab.shared.model.*
import kotlin.test.*

class SourcedOpeningCatalogTest {
    private fun route(id: String, name: String, moves: String): OpeningRecord {
        var board = BoardPosition.starting()
        val uci = moves.split(" ")
        val san = uci.map { ChessMove.fromUci(it).let { move -> board.san(move).also { board = board.apply(move) } } }
        return OpeningRecord(id, "C60", name, "Ruy Lopez", uci, san, board.toFen(), board.positionKey, emptyList())
    }
    private val routes = listOf(
        route("morphy", "Ruy Lopez: Morphy", "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6"),
        route("berlin", "Ruy Lopez: Berlin", "e2e4 e7e5 g1f3 b8c6 f1b5 g8f6"))
    private fun catalog(rows: List<OpeningRecord> = routes): SourcedOpeningCatalog {
        val source = ContentSource("fixture", SourceKind.OPENING_TAXONOMY, "Fixture taxonomy", "https://example.invalid", "fixture-v1",
            "CC0-1.0", "https://example.invalid/license", "https://example.invalid/evidence", "Fixture authors", "2026-10-01", true,
            "Fixture", "Two routes", emptyList())
        return SourcedOpeningCatalog(ContentManifest(packId = "fixture-v1", source = source, retrievedAt = "2026-10-01",
            inputs = emptyList(), coverage = CoverageCounts(rows.size, rows.size, 0, 0), files = emptyList(), limitations = emptyList(), snapshotLockSha256 = "0".repeat(64)), rows)
    }
    @Test fun allRoutesRemainSeparateAndSearchableWithHonestSourceScope() {
        val catalog = catalog(); val opening = catalog.openings.single()
        assertEquals(setOf("morphy", "berlin"), opening.variations.map { it.id }.toSet())
        assertNull(opening.historicalGame); assertEquals("CC0-1.0", opening.provenance?.license)
        assertEquals(opening, catalog.search("berlin C60").single()); assertTrue(catalog.search("unknown").isEmpty())
        assertEquals(opening, catalog.search("Ruy López").single())
        assertTrue(opening.variations.all { "not available" in it.whiteIdea && "not available" in it.blackIdea })
    }
    @Test fun sourcedGraphBranchesReturnAndBothColorsUseOriginalMoves() {
        val graph = LessonGraph.fromOpening(catalog().openings.single())
        assertTrue(graph.paths.values.all { it.kind == LessonPathKind.SOURCED_OPENING })
        for (side in PieceColor.entries) {
            val before = graph.start(side, "morphy").jump(5)
            val branch = before.branches().single { it.pathId == "berlin" }
            val switched = before.diverge(branch).next()
            assertEquals("g8f6", switched.lastMove?.move?.uci)
            assertEquals(before.position, switched.returnToBranch().position)
            assertEquals(side, LessonReplay.restore(graph, switched.snapshot()).playerSide)
        }
    }
    @Test fun endpointRecognitionHandlesTransposedMoveOrderAndLastKnown() {
        val boards = mutableListOf(BoardPosition.starting())
        for (uci in "g1f3 b8c6 e2e4 e7e5 f1b5 g8f6".split(" ")) boards += boards.last().apply(ChessMove.fromUci(uci))
        assertEquals("Ruy Lopez: Berlin", catalog().identify(boards)?.variationName)
        boards += boards.last().apply(ChessMove.fromUci("d2d3"))
        assertEquals(OpeningMatchKind.OUT_OF_BOOK, catalog().identify(boards)?.kind)
        assertEquals(6, catalog().identify(boards)?.matchedPly)
    }
    @Test fun duplicateIdsAreRejectedInsteadOfDroppingRoutes() {
        assertFailsWith<IllegalArgumentException> { catalog(listOf(routes.first(), routes.first())) }
    }
}
