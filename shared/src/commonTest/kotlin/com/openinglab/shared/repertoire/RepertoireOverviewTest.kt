// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.repertoire

import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.data.OpeningCatalog
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.model.PieceColor
import kotlin.test.*

class RepertoireOverviewTest {
    private fun book(id: String, pgn: String) = RepertoireBook(LessonGraph.fromPgn(Pgn.parse(pgn), id))

    @Test fun compatibleFamiliesUnionPositionsAndRepliesWithoutInventingRoutes() {
        val a = book("a", "1. e4 e5 2. Nf3 Nc6 3. Bb5 *")
        val b = book("b", "1. e4 c5 2. Nf3 d6 3. d4 *")
        val pa = a.seed(PieceColor.WHITE, "original"); val pb = b.seed(PieceColor.WHITE, "original")
        val builder = RepertoireOverviewBuilder(PieceColor.WHITE)
        builder.add(a, pa); builder.add(b, pb)
        val result = builder.build()
        assertEquals(2, result.members.size); assertTrue(result.conflicts.isEmpty())
        assertEquals((pa.preferredMoves.keys + pb.preferredMoves.keys).size, result.preferredPositions)
        assertEquals(4, result.includedReplies)
        assertEquals((a.coverage(pa).reachedPositionKeys + b.coverage(pb).reachedPositionKeys).size, result.reachedPositions)
        assertEquals(1, result.members[0].fittingRoutes); assertEquals(1, result.members[1].fittingRoutes)
        assertEquals(listOf("original"), a.coverage(pa).eligiblePathIds)
        assertEquals(pa, a.seed(PieceColor.WHITE, "original")) // No learner writes or route synthesis.
    }

    @Test fun conflictingRootChoicesRetainBothOriginsAndDoNotChooseAWinner() {
        val a = book("king-pawn", "1. e4 e5 *"); val b = book("queen-pawn", "1. d4 d5 *")
        val builder = RepertoireOverviewBuilder(PieceColor.WHITE)
        builder.add(a, a.seed(PieceColor.WHITE, "original")); builder.add(b, b.seed(PieceColor.WHITE, "original"))
        val conflict = builder.build().conflicts.single()
        assertEquals(a.graph.initialPosition.positionKey, conflict.positionKey)
        assertEquals(setOf("e4", "d4"), conflict.choices.map { it.san }.toSet())
        assertEquals(setOf("e2e4", "d2d4"), conflict.choices.map { it.uci }.toSet())
        assertTrue(conflict.choices.flatMap { it.origins }.all { it.pathId == "original" && it.ply == 0 })
    }

    @Test fun transposedPositionsExposeTheActualPerFamilyCursor() {
        val a = book("a", "1. d4 d5 2. Nf3 Nf6 3. c4 *")
        val b = book("b", "1. Nf3 d5 2. d4 Nf6 3. Bf4 *")
        val builder = RepertoireOverviewBuilder(PieceColor.WHITE)
        builder.add(a, a.seed(PieceColor.WHITE, "original")); builder.add(b, b.seed(PieceColor.WHITE, "original"))
        val key = a.graph.paths.getValue("original").positions[4].positionKey
        val conflict = builder.build().conflicts.single { it.positionKey == key }
        assertEquals(setOf("c4", "Bf4"), conflict.choices.map { it.san }.toSet())
        assertEquals(listOf(4, 4), conflict.choices.flatMap { it.origins }.map { it.ply })
    }

    @Test fun dormantChoicesAfterAnExcludedReplyDoNotInflateCombinedScope() {
        val a = book("a", "1. e4 e5 2. Nf3 Nc6 3. Bb5 *")
        val policy = a.seed(PieceColor.WHITE, "original")
        val afterE4 = a.graph.paths.getValue("original").positions[1].positionKey
        val excluded = a.include(policy, afterE4, "e7e5", false)
        val builder = RepertoireOverviewBuilder(PieceColor.WHITE); builder.add(a, excluded)
        val result = builder.build()
        assertEquals(1, result.preferredPositions); assertEquals(0, result.includedReplies)
        assertEquals(2, result.reachedPositions); assertEquals(0, result.members.single().fittingRoutes)
        assertEquals(1, result.members.single().unansweredBranches)
        assertEquals(3, excluded.preferredMoves.size) // Retained, not counted as reachable.
    }

    @Test fun missingAndChangedMembersRemainVisibleWithoutInventingCoverage() {
        val a = book("a", "1. e4 e5 *"); val b = book("b", "1. d4 d5 *")
        val builder = RepertoireOverviewBuilder(PieceColor.WHITE)
        builder.unavailable(a.seed(PieceColor.WHITE, "original"), RepertoireMemberStatus.UNAVAILABLE)
        builder.unavailable(b.seed(PieceColor.WHITE, "original"), RepertoireMemberStatus.CHANGED)
        val result = builder.build()
        assertEquals(2, result.unavailableMembers); assertEquals(2, result.members.size)
        assertEquals(0, result.preferredPositions); assertEquals(0, result.reachedPositions)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test fun duplicateIdentityWrongColorAndChangedContentFailClosed() {
        val a = book("a", "1. e4 e5 *"); val p = a.seed(PieceColor.WHITE, "original")
        val builder = RepertoireOverviewBuilder(PieceColor.WHITE); builder.add(a, p)
        assertFailsWith<IllegalArgumentException> { builder.add(a, p) }
        assertFailsWith<IllegalArgumentException> { RepertoireOverviewBuilder(PieceColor.BLACK).add(a, p) }
        assertFailsWith<IllegalArgumentException> { RepertoireOverviewBuilder(PieceColor.WHITE).add(a, p.copy(contentVersion = "f".repeat(64))) }
    }

    @Test fun workerCanCancelDuringPrefixAndOriginTraversal() {
        val a = book("a", "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 *"); val p = a.seed(PieceColor.WHITE, "original")
        var calls = 0
        assertFailsWith<IllegalStateException> {
            RepertoireOverviewBuilder(PieceColor.WHITE).add(a, p) { if (++calls > 8) error("cancel fixture") }
        }
        assertEquals(9, calls)
    }

    @Test fun oppositeColorChoicesAreIndependentlyConsistentAndRevisionIsRetained() {
        val graph = LessonGraph.fromOpening(OpeningCatalog.byId("ruy-lopez")); val a = RepertoireBook(graph)
        val white = a.seed(PieceColor.WHITE, "ruy-main").copy(revision = 12)
        val black = a.seed(PieceColor.BLACK, "ruy-berlin").copy(revision = 8)
        for (p in listOf(white, black)) {
            val builder = RepertoireOverviewBuilder(p.side); builder.add(a, p)
            assertTrue(builder.build().conflicts.isEmpty()); assertEquals(p, builder.build().members.single().policy)
        }
    }
}
