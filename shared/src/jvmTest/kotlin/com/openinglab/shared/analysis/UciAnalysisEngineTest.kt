// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.analysis

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.model.PieceColor
import kotlinx.coroutines.*
import kotlin.test.*

class UciAnalysisEngineTest {
    private val identity = EngineIdentity("Fixture", "1", "a".repeat(64), mapOf("nn-aaaaaaaaaaaa.nnue" to "a".repeat(64)))
    private val start = AnalysisPosition(BoardPosition.START_FEN)
    private fun info(move: String, rank: Int = 1, depth: Int = 8, score: String = "cp 30") =
        "info depth $depth multipv $rank score $score nodes 100 time 3 pv $move"
    private val hello = listOf("id name Fixture 1", "option name Threads type spin default 1 min 1 max 1024",
        "option name Hash type spin default 16 min 1 max 1000", "option name MultiPV type spin default 1 min 1 max 500",
        "option name Ponder type check default false", "option name Clear Hash type button",
        "option name EvalFile type string default nn-aaaaaaaaaaaa.nnue", "uciok", "readyok")
    private class Fake(lines: List<String>, val hang: Boolean = false) : UciTransport {
        val remaining = ArrayDeque(lines)
        val sent = mutableListOf<String>()
        var closed = false
        override suspend fun send(command: String) { sent += command }
        override suspend fun readLine(): String? = if (remaining.isNotEmpty()) remaining.removeFirst()
            else if (hang) awaitCancellation() else null
        override fun close() { closed = true }
    }
    private fun engine(fake: Fake) = UciAnalysisEngine(identity, UciTransportFactory { fake })
    private fun fake(vararg search: String) = Fake(hello + search.toList())
    private fun search(vararg lines: String) = listOf("readyok") + lines

    @Test fun evaluatesOriginalSeparatelyWithSameBudgetAndColdHashAndNoPonder() = runBlocking {
        val fake = Fake(hello + search(info("e2e4 e7e5"), info("d2d4 d7d5", 2, score = "cp 25"), "bestmove e2e4") +
            search(info("d2d4 d7d5", score = "cp 26"), "bestmove d2d4"))
        val result = engine(fake).analyze(start, "d2d4", AnalysisBudget(multiPv = 2))
        assertEquals(listOf("e4", "e5"), result.alternatives!!.lines.first().san)
        assertEquals("d2d4", result.original!!.bestMove)
        assertEquals(MoveComparison.NEAR_EQUAL_AT_BUDGET, result.compareOriginal())
        assertEquals(2, fake.sent.count { it == "ucinewgame" })
        assertEquals(2, fake.sent.count { it == "setoption name Clear Hash" })
        assertEquals(listOf("go depth 16 nodes 50000 movetime 1000", "go depth 16 nodes 50000 movetime 1000 searchmoves d2d4"), fake.sent.filter { it.startsWith("go ") })
        assertTrue("setoption name Threads value 1" in fake.sent)
        assertTrue("setoption name Ponder value false" in fake.sent)
        assertTrue(fake.closed); assertEquals(listOf("stop", "quit"), fake.sent.takeLast(2))
    }
    @Test fun blackScoresAndMatesAreRootRelativeNotWhiteRelative() = runBlocking {
        val root = start.copy(moves = listOf("g2g4", "e7e5", "f2f3"))
        val fake = fake("readyok", info("d8h4", score = "mate 1"), "bestmove d8h4")
        val result = engine(fake).analyze(root, budget = AnalysisBudget(multiPv = 1))
        assertEquals(PieceColor.BLACK, result.rootSide)
        assertEquals(listOf("Qh4#"), result.alternatives!!.lines.single().san)
        assertEquals(EngineScore.Mate(-1), result.alternatives.lines.single().score.forSide(result.rootSide, PieceColor.WHITE))
        assertEquals(MoveComparison.NOT_COMPARABLE, result.compareOriginal())
    }
    @Test fun coherentDepthsDoNotMixAndIncompleteCandidateSetsStayExplicit() = runBlocking {
        val fake = fake("readyok", info("e2e4", depth = 5), info("d2d4", 2, 5), info("g1f3", depth = 6), "bestmove g1f3")
        val result = engine(fake).analyze(start, budget = AnalysisBudget(multiPv = 2)).alternatives!!
        assertFalse(result.completeCandidateSet)
        assertEquals(listOf("g1f3"), result.lines.map { it.uci.first() })
        assertEquals(6, result.lines.single().depth)
    }
    @Test fun repeatedFirstRankInvalidatesOtherCandidatesAtSameDepth() = runBlocking {
        val fake = fake("readyok", info("e2e4"), info("d2d4", 2), info("g1f3"), "bestmove g1f3")
        assertFalse(engine(fake).analyze(start, budget = AnalysisBudget(multiPv = 2)).alternatives!!.completeCandidateSet)
    }
    @Test fun boundsDoNotBecomeExactMoveComparisons() = runBlocking {
        val fake = Fake(hello + search(info("e2e4", score = "cp 30 upperbound"), "bestmove e2e4") +
            search(info("d2d4", score = "cp -120"), "bestmove d2d4"))
        val result = engine(fake).analyze(start, "d2d4", AnalysisBudget(multiPv = 1))
        assertEquals(MoveComparison.NOT_COMPARABLE, result.compareOriginal())
        assertEquals(ScoreBound.UPPER, result.alternatives!!.lines.single().score.bound)
    }
    @Test fun unequalScoresAreNotLabeledBlunders() = runBlocking {
        val fake = Fake(hello + search(info("e2e4", score = "cp 60"), "bestmove e2e4") +
            search(info("d2d4", score = "cp -30"), "bestmove d2d4"))
        assertEquals(MoveComparison.DIFFERENT_AT_BUDGET, engine(fake).analyze(start, "d2d4", AnalysisBudget(multiPv = 1)).compareOriginal())
    }
    @Test fun illegalOriginalOrHistoryNeverStartsEngine() = runBlocking {
        var opened = false
        val engine = UciAnalysisEngine(identity, UciTransportFactory { opened = true; fake() })
        assertFailsWith<IllegalArgumentException> { engine.analyze(start, "e2e5") }
        assertFailsWith<IllegalArgumentException> { engine.analyze(start.copy(moves = listOf("e2e5"))) }
        assertFalse(opened)
    }
    @Test fun terminalMateDrawAndRepetitionReturnWithoutStartingProcess() = runBlocking {
        var opened = false
        val engine = UciAnalysisEngine(identity, UciTransportFactory { opened = true; fake() })
        assertEquals(com.openinglab.shared.chess.PositionStatus.CHECKMATE,
            engine.analyze(start.copy(moves = listOf("g2g4", "e7e5", "f2f3", "d8h4"))).terminal)
        val repeated = List(4) { listOf("g1f3", "g8f6", "f3g1", "f6g8") }.flatten()
        assertEquals(com.openinglab.shared.chess.PositionStatus.FIVEFOLD_REPETITION, engine.analyze(start.copy(moves = repeated)).terminal)
        assertEquals(com.openinglab.shared.chess.PositionStatus.INSUFFICIENT_MATERIAL,
            engine.analyze(AnalysisPosition("8/8/8/8/8/8/6k1/4K3 w - - 0 1")).terminal)
        assertFalse(opened)
    }
    @Test fun rejectsIllegalPvDuplicateCandidatesAndMalformedMetrics() = runBlocking {
        for (lines in listOf(listOf(info("e2e5")), listOf(info("e2e4 e7e6 e4e6")),
            listOf(info("e2e4"), info("e2e4", 2)), listOf(info("e2e4").replace("nodes 100", "nodes -1")),
            listOf(info("e2e4", score = "mate 0")))) {
            val fake = Fake(hello + search(*(lines + "bestmove e2e4").toTypedArray()))
            assertFails { engine(fake).analyze(start, budget = AnalysisBudget(multiPv = 2)) }
            assertTrue(fake.closed)
        }
    }
    @Test fun rejectsIdentityOrNetworkMismatchAndEarlyExitAndNoScoredBestMove() = runBlocking {
        for (fake in listOf(Fake(hello.map { it.replace("Fixture 1", "Fixture 2") }),
            Fake(hello.map { it.replace("nn-aaaaaaaaaaaa", "nn-bbbbbbbbbbbb") }), Fake(hello.take(2)),
            fake("readyok", "bestmove e2e4"))) {
            assertFailsWith<EngineProtocolException> { engine(fake).analyze(start) }
            assertTrue(fake.closed)
        }
    }
    @Test fun restrictedSearchCannotSilentlyEvaluateAnotherMove() = runBlocking {
        val fake = Fake(hello + search(info("e2e4"), "bestmove e2e4") + search(info("e2e4"), "bestmove e2e4"))
        assertFailsWith<EngineProtocolException> { engine(fake).analyze(start, "d2d4", AnalysisBudget(multiPv = 1)) }
        assertTrue(fake.closed)
    }
    @Test fun timeoutStopsQuitsAndClosesHungEngine() = runBlocking {
        val fake = Fake(hello + "readyok", hang = true)
        assertFailsWith<TimeoutCancellationException> { engine(fake).analyze(start, budget = AnalysisBudget(moveTimeMillis = 1)) }
        assertTrue(fake.closed); assertEquals(listOf("stop", "quit"), fake.sent.takeLast(2))
    }
    @Test fun callerCancellationTerminatesRatherThanReturningStaleAnalysis() = runBlocking {
        val fake = Fake(hello + "readyok", hang = true)
        val job = launch { engine(fake).analyze(start) }
        yield(); job.cancelAndJoin()
        assertTrue(fake.closed); assertEquals(listOf("stop", "quit"), fake.sent.takeLast(2))
    }
    @Test fun oversizedOutputFailsClosed() = runBlocking {
        val fake = fake("readyok", "info string " + "x".repeat(8_193))
        assertFailsWith<EngineProtocolException> { engine(fake).analyze(start) }
        assertTrue(fake.closed)
    }
    @Test fun reusedRootPreservesRepetitionHistoryAndIndependentPvSearches() = runBlocking {
        val history = List(3) { listOf("g1f3", "g8f6", "f3g1", "f6g8") }.flatten()
        val position = start.copy(moves = history)
        val root = position.board()
        val fixture = Fake(hello + search(info("g1f3 g8f6 f3g1 f6g8", depth = 6, score = "cp 0"),
            info("d2d4 d7d5", depth = 8), "bestmove d2d4") +
            search(info("g1f3 g8f6 f3g1 f6g8", score = "cp 0"), "bestmove g1f3"))
        val result = engine(fixture).analyze(position, "g1f3", AnalysisBudget(multiPv = 1))
        assertEquals(listOf("d4", "d5"), result.alternatives!!.lines.single().san)
        assertEquals(listOf("Nf3", "Nf6", "Ng1", "Ng8"), result.original!!.lines.single().san)
        assertEquals(root, position.board())
        assertEquals(2, fixture.sent.count { it == position.command() })
        assertTrue(fixture.closed)
    }
    @Test fun reusedRootStillRejectsPvContinuingAfterHistoryDependentTerminalDraw() = runBlocking {
        val position = start.copy(moves = List(3) { listOf("g1f3", "g8f6", "f3g1", "f6g8") }.flatten())
        val fixture = fake("readyok", info("g1f3 g8f6 f3g1 f6g8 e2e4"), "bestmove g1f3")
        assertFailsWith<EngineProtocolException> { engine(fixture).analyze(position, budget = AnalysisBudget(multiPv = 1)) }
        assertTrue(fixture.closed)
    }
}
