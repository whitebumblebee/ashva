// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.course

import com.openinglab.shared.model.PieceColor
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Study must branch along the way: deep main lines, one offer per different next move, joined move orders continued. */
class DeepCourseBranchingTest {
    @Test fun realPackMainLinesRunDeepAndOfferFewDistinctBranchesAlongTheWay() {
        val file = File("content/courses/ruy-lopez/v1/course.json")
        assumeTrue("local course pack missing", file.isFile)
        val pack = DeepCourseCatalog.parse(file.readText())
        val checked = DeepCourseValidator.validate(pack)
        for (chapter in pack.chapters.filter { it.kind == "REPERTOIRE" }) {
            val view = DeepCourseCatalog.present(pack, chapter, checked.getValue(chapter.id))
            val graph = view.lessonGraph()
            val main = view.opening.mainLine
            var replay = graph.start(PieceColor.WHITE, main.id)
            var branchPoints = 0
            var maxOffers = 0
            while (true) {
                val offers = replay.branches()
                assertEquals(offers.size, offers.map { it.nextMove.move }.distinct().size, "${chapter.id}: one offer per move")
                if (offers.isNotEmpty()) branchPoints++
                maxOffers = maxOf(maxOffers, offers.size)
                if (replay.atEnd) break
                replay = replay.next()
            }
            println("BRANCHING ${chapter.id}: main line ${main.steps.size} plies, $branchPoints branch points, max $maxOffers offers")
            if (graph.paths.size >= 20) assertTrue(main.steps.size >= 16, "${chapter.id}: main line too short (${main.steps.size})")
            assertTrue(branchPoints >= 2 || graph.paths.size < 3, "${chapter.id}: study offers no branches along the main line")
            assertTrue(maxOffers <= 15, "${chapter.id}: $maxOffers offers at one position")
        }
    }
}
