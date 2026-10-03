// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.repertoire

import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.model.PieceColor
import kotlin.test.*

class RepertoireSetTest {
    private fun book(id: String, pgn: String) = RepertoireBook(LessonGraph.fromPgn(Pgn.parse(pgn), id))
    private fun set(vararg policies: RepertoirePolicy) = RepertoireSet("set-test", "My book", policies.first().side,
        policies.map { RepertoirePolicyRef(it.id, it.revision) })

    @Test fun compatibleFamiliesProduceExactRevisionQueueAndSeparateOriginalPaths() {
        val a = book("spanish", "1. e4 e5 2. Nf3 Nc6 3. Bb5 *")
        val b = book("sicilian", "1. e4 c5 2. Nf3 d6 3. d4 *")
        val pa = a.seed(PieceColor.WHITE, "original").copy(revision = 4)
        val pb = b.seed(PieceColor.WHITE, "original").copy(revision = 7)
        val selected = set(pb, pa)
        val planner = RepertoireSetPlanner(selected)
        planner.add(a, pa); planner.add(b, pb)
        val plan = planner.build()
        assertTrue(plan.ready)
        assertEquals(listOf("sicilian", "spanish"), plan.items.map { it.lessonId })
        assertEquals(selected.members, plan.items.map { it.policy })
        assertEquals(listOf("original", "original"), plan.items.map { it.pathId })
        assertEquals(pa, a.seed(PieceColor.WHITE, "original").copy(revision = 4))
        assertEquals(plan.items[1], RepertoireSetSession(plan, 1).item)
    }

    @Test fun conflictingChoicesBlockUnifiedPracticeWithoutOverwritingEitherPolicy() {
        val a = book("e4", "1. e4 e5 *"); val b = book("d4", "1. d4 d5 *")
        val pa = a.seed(PieceColor.WHITE, "original"); val pb = b.seed(PieceColor.WHITE, "original")
        val planner = RepertoireSetPlanner(set(pa, pb)); planner.add(a, pa); planner.add(b, pb)
        val plan = planner.build()
        assertFalse(plan.ready); assertEquals(2, plan.items.size)
        assertEquals(setOf("e2e4", "d2d4"), plan.overview.conflicts.single().choices.map { it.uci }.toSet())
        assertFailsWith<IllegalArgumentException> { RepertoireSetSession(plan, 0) }
    }

    @Test fun missingChangedAndIncompleteMembersAreNotDroppedOrCalledReady() {
        val a = book("a", "1. e4 e5 *"); val b = book("b", "1. e4 c5 *")
        val pa = a.seed(PieceColor.WHITE, "original"); val pb = b.seed(PieceColor.WHITE, "original")
        val missing = RepertoireSetPlanner(set(pa, pb)); missing.add(a, pa)
        assertFailsWith<IllegalArgumentException> { missing.build() }
        missing.unavailable(pb, RepertoireMemberStatus.UNAVAILABLE)
        assertFalse(missing.build().ready); assertEquals(2, missing.build().overview.members.size)
        val empty = a.include(pa, a.graph.paths.getValue("original").positions[1].positionKey, "e7e5", false)
        val planner = RepertoireSetPlanner(set(empty)); planner.add(a, empty)
        assertFalse(planner.build().ready); assertTrue(planner.build().items.isEmpty())
    }

    @Test fun blackAndWhiteStaySeparateAndRevisionMismatchIsRejected() {
        val a = book("a", "1. e4 c5 *"); val white = a.seed(PieceColor.WHITE, "original")
        val black = a.seed(PieceColor.BLACK, "original")
        val planner = RepertoireSetPlanner(set(black)); planner.add(a, black); assertTrue(planner.build().ready)
        assertFailsWith<IllegalArgumentException> { RepertoireSetPlanner(set(white)).add(a, white.copy(revision = 1)) }
        assertFailsWith<IllegalArgumentException> { RepertoireSetPlanner(set(white).copy(side = PieceColor.BLACK)).add(a, white) }
        assertFailsWith<IllegalArgumentException> { set(white, white).validate() }
    }

    @Test fun memberChecksAndQueueConstructionAreCancellable() {
        val a = book("a", "1. e4 e5 2. Nf3 Nc6 *"); val p = a.seed(PieceColor.WHITE, "original")
        assertFailsWith<IllegalStateException> { RepertoireSetPlanner(set(p)).add(a, p) { error("cancel fixture") } }
        val planner = RepertoireSetPlanner(set(p)); planner.add(a, p)
        assertFailsWith<IllegalStateException> { planner.build { error("cancel fixture") } }
    }
}
