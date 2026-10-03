// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.repertoire

import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.data.OpeningCatalog
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.lesson.LessonReplay
import com.openinglab.shared.model.PieceColor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class RepertoirePolicyTest {
    private val graph = LessonGraph.fromOpening(OpeningCatalog.byId("ruy-lopez"))
    private val book = RepertoireBook(graph)

    @Test fun selectedRouteSeedsSeparateWhiteAndBlackPolicies() {
        for (side in PieceColor.entries) {
            val policy = book.seed(side, "ruy-main")
            assertEquals(listOf("ruy-main"), book.coverage(policy).eligiblePathIds)
            assertTrue(book.coverage(policy).gaps.isEmpty())
            assertTrue(book.coverage(policy).excludedOpponentReplies > 0)
            assertEquals(policy, Json.decodeFromString<RepertoirePolicy>(Json.encodeToString(policy)))
        }
        assertNotEquals(book.policyId(PieceColor.WHITE), book.policyId(PieceColor.BLACK))
    }

    @Test fun includedNewReplyRevealsUnansweredLearnerBranch() {
        val policy = book.seed(PieceColor.WHITE, "ruy-main")
        val key = graph.paths.getValue("ruy-main").positions[5].positionKey
        val expanded = book.include(policy, key, "g8f6", true)
        assertEquals(1, expanded.revision)
        assertTrue(book.coverage(expanded).gaps.any { it.pathId == "ruy-berlin" && it.ply == 6 && it.kind == RepertoireGapKind.CHOOSE_LEARNER_MOVE })
        assertEquals(listOf("ruy-main"), book.coverage(expanded).eligiblePathIds)
        val complete = book.adoptRoute(expanded, "ruy-berlin")
        assertEquals(setOf("ruy-main", "ruy-berlin"), book.coverage(complete).eligiblePathIds.toSet())
        assertTrue(book.coverage(complete).gaps.isEmpty())
        assertTrue(book.practiceGraph(complete).start(PieceColor.WHITE, "ruy-main").jump(5).branches().any { it.pathId == "ruy-berlin" })
    }

    @Test fun practiceExcludesOtherChoicesButExplorerRemainsIntact() {
        val policy = book.seed(PieceColor.BLACK, "ruy-main")
        val practice = book.practiceGraph(policy).start(PieceColor.BLACK).jump(5)
        assertTrue(practice.branches().isEmpty())
        assertTrue(graph.start(PieceColor.BLACK).jump(5).branches().any { it.pathId == "ruy-berlin" })
        assertEquals("a7a6", practice.nextMove?.move?.uci)
        assertEquals(practice.position, LessonReplay.restore(practice.graph, practice.snapshot()).position)
    }

    @Test fun emptyReplySetAndIncompletePolicyCannotBecomePractice() {
        val policy = book.seed(PieceColor.WHITE, "ruy-main")
        val key = graph.initialPosition.apply(com.openinglab.shared.model.ChessMove.fromUci("e2e4")).positionKey
        val empty = book.include(policy, key, "e7e5", false)
        assertTrue(book.coverage(empty).eligiblePathIds.isEmpty())
        assertEquals(RepertoireGapKind.INCLUDE_OPPONENT_REPLY, book.coverage(empty).gaps.single().kind)
        assertFailsWith<IllegalArgumentException> { book.practiceGraph(empty) }
    }

    @Test fun transposedPositionsShareChoicesWithoutInventingSourceMoveOrders() {
        val g = LessonGraph.fromPgn(Pgn.parse("1. d4 (1. Nf3 d5 2. d4 Nf6 3. Bf4) d5 2. Nf3 Nf6 3. c4 *"))
        val b = RepertoireBook(g)
        val original = g.paths.getValue("original")
        val alternate = g.paths.values.single { it.origin != null }
        assertEquals(original.positions[4].positionKey, alternate.positions[4].positionKey)
        var p = b.seed(PieceColor.BLACK, original.id)
        p = b.adoptRoute(p, alternate.id)
        // Both White replies are included at the transposed position; each original prefix records only one.
        assertEquals(setOf(original.id, alternate.id), b.coverage(p).eligiblePathIds.toSet())
        assertTrue(b.coverage(p).gaps.any { it.kind == RepertoireGapKind.SOURCE_CONTINUATION })
        assertEquals(g.paths.keys, b.practiceGraph(p).paths.keys)
        assertEquals(original.moves, g.paths.getValue(original.id).moves)
    }

    @Test fun repetitionIsFiniteAndConflictingPreferredMoveRemainsVisible() {
        val g = LessonGraph.fromPgn(Pgn.parse("1. Nf3 Nf6 2. Ng1 Ng8 3. d4 d5 *"))
        val b = RepertoireBook(g)
        val p = b.seed(PieceColor.WHITE, "original")
        assertTrue(b.coverage(p).gaps.any { it.kind == RepertoireGapKind.SOURCE_CONTINUATION })
        assertTrue(b.coverage(p).eligiblePathIds.isEmpty())
        assertEquals(g.initialPosition.positionKey, g.paths.getValue("original").positions[4].positionKey)
    }

    @Test fun choicesMustBeRecordedLegalAndOnTheCorrectSide() {
        val p = book.seed(PieceColor.WHITE, "ruy-main")
        assertFailsWith<IllegalArgumentException> { book.prefer(p, graph.initialPosition.positionKey, "d2d4") }
        assertFailsWith<IllegalArgumentException> { book.include(p, graph.initialPosition.positionKey, "e2e4", true) }
        assertFailsWith<IllegalArgumentException> { book.validate(p.copy(contentVersion = "f".repeat(64))) }
        assertFailsWith<IllegalArgumentException> { book.validate(p.copy(schemaVersion = 2)) }
        assertFailsWith<IllegalArgumentException> { book.validate(p.copy(revision = -1)) }
    }

    @Test fun snapshotIdentityIgnoresNarrationButChangesForRecordedMoves() {
        val opening = OpeningCatalog.byId("ruy-lopez")
        val renamed = opening.copy(variations = opening.variations.map { it.copy(description = "Revised explanation") })
        assertEquals(book.contentVersion, RepertoireBook(LessonGraph.fromOpening(renamed)).contentVersion)
        val smaller = opening.copy(variations = opening.variations.filter { it.id != "ruy-berlin" })
        assertNotEquals(book.contentVersion, RepertoireBook(LessonGraph.fromOpening(smaller)).contentVersion)
    }
}
