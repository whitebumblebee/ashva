// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.course

import com.openinglab.shared.data.PackValidationCache
import com.openinglab.shared.data.PackValidationIdentity
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.tactics.*
import kotlin.test.*

class ValidationCacheTest {
    private class Markers(private val identity: PackValidationIdentity, val disk: MutableSet<String> = mutableSetOf()) : PackValidationCache {
        override fun isValidated(section: String) = identity.key(section) in disk
        override fun markValidated(section: String) { disk.add(identity.key(section)) }
    }
    private val identity = PackValidationIdentity("a".repeat(64), 19, DeepCourseValidator.VERSION)
    private fun pack(): DeepCoursePack {
        var board = BoardPosition.starting()
        val moves = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "g8f6", "e1g1", "f8e7")
        val nodes = moves.mapIndexed { i, token ->
            val transition = board.sanAndPlay(ChessMove.fromUci(token)); board = transition.position
            CourseNode("n$i", if (i == 0) null else "n${i - 1}", transition.san, token, "MAIN", "Move", "Text", "", "Checked", 1.0,
                verdict = if (i == moves.lastIndex) Verdict("EQUAL", 0, "White", "Black", "Checked") else null)
        }
        return DeepCoursePack(1, "cached", 1, "Cached", "BOTH", "Test", "Test",
            CourseProvenance(listOf(CourseSource("s", "Test", "https://example.invalid", "CC0-1.0", 1, "Test")),
                "Engine", "Budget", "Writer", "2026-10-06", "Label", "Limitations"),
            listOf(CourseChapter("c", "Chapter", "REPERTOIRE", "Intro", 2, nodes)))
    }
    @Test fun successfulValidationPersistsAndCachedReplayRetainsExactGraphsAndHistory() {
        val pack = pack(); val markers = Markers(identity)
        val first = DeepCourseCatalog(listOf(pack), validateOnDemand = true, validationCache = markers)
        first.validateRemaining()
        assertTrue(markers.isValidated("pack"))
        val second = DeepCourseCatalog(listOf(pack), validateOnDemand = true, validationCache = Markers(identity, markers.disk))
        var work = 0
        second.validateRemaining { work++ }
        assertEquals(0, work)
        val full = DeepCourseCatalog(listOf(pack)).chapters.single()
        val cached = second.chapters.single()
        assertEquals(full.opening, cached.opening)
        assertEquals(full.nodePositions, cached.nodePositions)
        assertEquals(full.lessonGraph().paths, cached.lessonGraph().paths)
        assertEquals(full.lessonGraph().nodes, cached.lessonGraph().nodes)
    }
    @Test fun checksumAppVersionAndValidatorChangesInvalidateMarkersIndependently() {
        val disk = mutableSetOf<String>(); Markers(identity, disk).markValidated("pack")
        assertTrue(Markers(identity, disk).isValidated("pack"))
        for (changed in listOf(identity.copy(sha256 = "b".repeat(64)), identity.copy(appVersionCode = 20), identity.copy(validatorVersion = 2))) {
            val markers = Markers(changed, disk)
            assertFalse(markers.isValidated("pack"))
            var work = 0
            DeepCourseCatalog(listOf(pack()), validateOnDemand = true, validationCache = markers).validateRemaining { work++ }
            assertTrue(work > 0)
            assertTrue(markers.isValidated("pack"))
        }
    }
    @Test fun invalidAndCancelledChaptersNeverCreateValidationProofs() {
        val original = pack()
        val invalid = original.copy(chapters = original.chapters.map { c -> c.copy(nodes = c.nodes.map { if (it.id == "n2") it.copy(san = "Nc3") else it }) })
        val markers = Markers(identity)
        assertFailsWith<IllegalArgumentException> { DeepCourseCatalog(listOf(invalid), validateOnDemand = true, validationCache = markers).validateRemaining() }
        assertTrue(markers.disk.isEmpty())
        val catalog = DeepCourseCatalog(listOf(original), validateOnDemand = true, validationCache = markers)
        assertFailsWith<IllegalStateException> { catalog.validateRemaining { error("Cancelled") } }
        assertTrue(markers.disk.isEmpty())
        assertFailsWith<IllegalArgumentException> { DeepCourseCatalog(listOf(invalid), validateOnDemand = true, validationCache = markers).chapters }
        assertTrue(markers.disk.isEmpty())
    }
    @Test fun tacticsFullValidationProofRequiresLegalSolutionsAndSuccessfulCompletion() {
        val puzzle = Puzzle("p", BoardPosition.START_FEN, listOf("e2e4", "e7e5"), 1000, listOf("opening"))
        val set = PuzzleSet("set", "Set", listOf("p"), "THEME")
        val good = TacticsPack(puzzles = listOf(puzzle), sets = listOf(set))
        val bad = good.copy(puzzles = listOf(puzzle.copy(moves = listOf("e2e5", "e7e5"))))
        val markers = Markers(identity)
        TacticsPackValidator.validateStructure(bad) // metadata alone must not authorize play
        assertFailsWith<IllegalArgumentException> { TacticsPackValidator.validateOnce(bad, markers) }
        assertFalse(markers.isValidated("pack"))
        assertFailsWith<IllegalStateException> { TacticsPackValidator.validateOnce(good, markers) { error("Cancelled") } }
        assertFalse(markers.isValidated("pack"))
        TacticsPackValidator.validateOnce(good, markers)
        assertTrue(markers.isValidated("pack"))
        TacticsPackValidator.validateOnce(good, Markers(identity, markers.disk))
        assertFailsWith<IllegalArgumentException> { TacticsPackValidator.validateSet(bad, set) }
    }
}
