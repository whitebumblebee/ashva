package com.openinglab.shared.lesson

import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.data.OpeningCatalog
import com.openinglab.shared.model.PieceColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LessonReplayTest {
    private val ruy = LessonGraph.fromOpening(OpeningCatalog.byId("ruy-lopez"))

    @Test fun allAuthoredLinesBecomeValidatedFinitePaths() {
        val graphs = OpeningCatalog.openings.map(LessonGraph::fromOpening)
        assertEquals(13, graphs.sumOf { it.paths.size })
        for (graph in graphs) for (path in graph.paths.values) {
            assertEquals(path.moves.size + 1, path.positions.size)
            assertTrue(path.positions.all { it.positionKey in graph.nodes })
            assertEquals(path.positions.last(), graph.start(PieceColor.WHITE, path.id).last().position)
        }
    }

    @Test fun replayNavigationIsDeterministicForBothPerspectives() {
        for (side in PieceColor.entries) {
            val replay = ruy.start(side)
            assertTrue(replay.atStart)
            assertEquals(0, replay.previous().ply)
            assertEquals(1, replay.next().ply)
            assertEquals(replay.position, replay.next().previous().position)
            assertEquals(16, replay.last().ply)
            assertEquals(16, replay.last().next().ply)
            assertEquals(replay.position, replay.last().first().position)
            assertEquals(side, replay.jump(5).playerSide)
            assertFailsWith<IllegalArgumentException> { replay.jump(-1) }
            assertFailsWith<IllegalArgumentException> { replay.jump(17) }
        }
    }

    @Test fun branchesAppearOnlyAtActualDifferentContinuations() {
        assertTrue(ruy.start(PieceColor.WHITE).branches().isEmpty())
        val berlin = ruy.start(PieceColor.BLACK).jump(5).branches().single()
        assertEquals("ruy-berlin", berlin.pathId)
        assertEquals("Nf6", berlin.nextMove.san)
        val exchange = ruy.start(PieceColor.WHITE).jump(6).branches().single()
        assertEquals("ruy-exchange", exchange.pathId)
        assertEquals("Bxc6", exchange.nextMove.san)
    }

    @Test fun branchingPreservesPositionAndExactReturnContext() {
        val original = ruy.start(PieceColor.BLACK).jump(5)
        val branch = original.diverge(original.branches().single())
        assertEquals(original.position, branch.position)
        assertEquals(original.moves.take(5), branch.moves.take(5))
        assertEquals("ruy-berlin", branch.pathId)
        assertEquals("Nf6", branch.nextMove?.san)
        val returned = branch.last().returnToBranch()
        assertEquals(original.position, returned.position)
        assertEquals(original.moves, returned.moves)
        assertEquals(original.ply, returned.ply)
        assertEquals(original.playerSide, returned.playerSide)
        assertFalse(returned.canReturn)
    }

    @Test fun nestedPgnBranchesReturnOneFrameAtATime() {
        val graph = LessonGraph.fromPgn(Pgn.parse("1. e4 (1. d4 d5 (1... Nf6 2. c4) 2. c4) e5 2. Nf3 *"))
        val root = graph.start(PieceColor.BLACK)
        val first = root.diverge(root.branches().single()).next()
        val nested = first.diverge(first.branches().single()).last()
        assertEquals(2, nested.branchDepth)
        assertEquals(LessonPathKind.ANNOTATED_VARIATION, nested.path.kind)
        val returned = nested.returnToBranch()
        assertEquals(first.position, returned.position)
        assertEquals(first.moves, returned.moves)
        assertEquals("d5", returned.nextMove?.san)
        assertEquals(root.position, returned.returnToBranch().position)
    }

    @Test fun transposedBranchesShareNodesButKeepPlayedHistory() {
        val graph = LessonGraph.fromPgn(Pgn.parse("1. d4 (1. Nf3 d5 2. d4 Nf6 3. Bf4) d5 2. Nf3 Nf6 3. c4 *"))
        val original = graph.start(PieceColor.WHITE).jump(4)
        val alternative = graph.paths.values.single { it.origin != null }
        assertEquals(original.position.positionKey, alternative.positions[4].positionKey)
        assertFalse(original.position.halfmoveClock == alternative.positions[4].halfmoveClock)
        assertEquals(2, graph.nodes.getValue(original.position.positionKey).continuations.size)
        val branched = original.diverge(original.branches().single())
        assertEquals(original.position, branched.position)
        assertEquals(original.moves.take(4), branched.moves.take(4))
        assertEquals(original.position.halfmoveClock + 1, branched.next().position.halfmoveClock)
        assertEquals(original.position, branched.next().returnToBranch().position)
    }

    @Test fun annotationsArePathSpecificNotNodeMetadata() {
        val graph = LessonGraph.fromPgn(Pgn.parse("1. d4 (1. Nf3 d5 2. d4 Nf6 {alternative context} 3. Bf4) d5 2. Nf3 Nf6 {original context} 3. c4 *"))
        val original = graph.paths.getValue("original")
        val alternate = graph.paths.values.single { it.origin != null }
        assertEquals(original.positions[4].positionKey, alternate.positions[4].positionKey)
        assertEquals(listOf("original context"), original.moves[3].annotation.comments)
        assertEquals(listOf("alternative context"), alternate.moves[3].annotation.comments)
    }

    @Test fun originalHistoricalSequenceAndResultAreNeverRewritten() {
        val graph = LessonGraph.fromPgn(Pgn.parse("[Result \"*\"] 1. e4 (1. d4 d5) e5 *"))
        val original = graph.paths.getValue("original")
        val replay = graph.start(PieceColor.WHITE)
        replay.diverge(replay.branches().single()).last()
        assertEquals(LessonPathKind.ORIGINAL_GAME, original.kind)
        assertEquals(listOf("e2e4", "e7e5"), original.moves.map { it.move.uci })
        assertEquals("*", original.result)
        assertEquals(original, graph.paths.getValue("original"))
    }

    @Test fun compactSnapshotsRestoreNestedBranchesColorAndCursor() {
        val graph = LessonGraph.fromPgn(Pgn.parse("1. e4 (1. d4 d5 (1... Nf6 2. c4) 2. c4) e5 *"))
        val first = graph.start(PieceColor.BLACK).let { it.diverge(it.branches().single()) }.next()
        val nested = first.diverge(first.branches().single()).next()
        val restored = LessonReplay.restore(graph, nested.snapshot())
        assertEquals(nested.pathId, restored.pathId)
        assertEquals(nested.position, restored.position)
        assertEquals(nested.moves, restored.moves)
        assertEquals(nested.playerSide, restored.playerSide)
        assertEquals(first.position, restored.returnToBranch().position)
        assertEquals(graph.initialPosition, restored.returnToBranch().returnToBranch().position)
    }

    @Test fun repeatedPositionsAndBranchDepthAreBounded() {
        val graph = LessonGraph.fromPgn(Pgn.parse("1. Nf3 (1. Nc3 Nc6 2. Nb1 Nb8) Nf6 2. Ng1 Ng8 *"))
        var replay = graph.start(PieceColor.WHITE).last()
        assertEquals(graph.initialPosition.positionKey, replay.position.positionKey)
        repeat(LessonReplay.MAX_BRANCH_DEPTH) {
            replay = replay.diverge(replay.branches().first()).last()
        }
        assertEquals(LessonReplay.MAX_BRANCH_DEPTH, replay.branchDepth)
        assertTrue(replay.branches().isEmpty())
        assertEquals(replay.position, LessonReplay.restore(graph, replay.snapshot()).position)
    }

    @Test fun customFenAndBlackFirstReplayKeepMoveNumbersAndSide() {
        val graph = LessonGraph.fromPgn(Pgn.parse("[SetUp \"1\"] [FEN \"7k/8/8/8/8/8/8/KR6 b - - 12 20\"] 20... Kh7 *"))
        val replay = graph.start(PieceColor.BLACK)
        assertEquals(20, replay.position.fullmoveNumber)
        assertEquals(PieceColor.BLACK, replay.position.sideToMove)
        assertEquals(21, replay.next().position.fullmoveNumber)
        assertEquals(replay.position, replay.last().previous().position)
    }

    @Test fun staleOrForgedBranchesAndInvalidSnapshotsAreRejected() {
        val replay = ruy.start(PieceColor.WHITE)
        val branch = replay.jump(5).branches().single()
        assertFailsWith<IllegalArgumentException> { replay.diverge(branch) }
        assertFailsWith<IllegalArgumentException> { LessonReplay.restore(ruy, ReplaySnapshot("ruy-main", PieceColor.WHITE, 99)) }
        assertFailsWith<IllegalArgumentException> { LessonReplay.restore(ruy, ReplaySnapshot("ruy-main", PieceColor.WHITE, 5,
            listOf(ReplayBranchVisit(5, "missing", 5)))) }
    }
}
