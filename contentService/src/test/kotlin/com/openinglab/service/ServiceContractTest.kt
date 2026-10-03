// SPDX-License-Identifier: Apache-2.0
package com.openinglab.service

import com.openinglab.shared.analysis.*
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.model.PieceColor
import kotlinx.coroutines.runBlocking
import kotlin.test.*

internal val testIdentity = EngineIdentity("Stockfish", "19", "a".repeat(64), mapOf("nn-1a298aa575a0.nnue" to "b".repeat(64)))
internal fun result(request: AnalysisRequest, identity: EngineIdentity = testIdentity): EngineAnalysis {
    val line = AnalyzedLine(1, 3, 5, 2, EngineScore.Centipawns(20), listOf("e2e4"), listOf("e4"))
    val alternatives = AnalysisSearch(listOf(line), "e2e4", 1, true)
    return EngineAnalysis(request.checked(), request.budget.checked(), identity, PieceColor.WHITE, alternatives,
        if (request.originalMove != null) alternatives else null)
}
class ServiceContractTest {
    @Test fun fullHistoryBudgetOriginalAndExactEngineBelongToCacheKey() {
        val request = AnalysisRequest(BoardPosition.START_FEN, budget = BudgetRequest(multiPv = 1))
        val key = analysisKey(request, testIdentity)
        assertNotEquals(key, analysisKey(request.copy(moves = listOf("g1f3", "g8f6", "f3g1", "f6g8")), testIdentity))
        assertNotEquals(key, analysisKey(request.copy(originalMove = "e2e4"), testIdentity))
        assertNotEquals(key, analysisKey(request.copy(budget = request.budget.copy(nodes = 999)), testIdentity))
        assertNotEquals(key, analysisKey(request, testIdentity.copy(binarySha256 = "c".repeat(64))))
        assertNotEquals(key, analysisKey(request, testIdentity.copy(networks = mapOf("nn-1a298aa575a0.nnue" to "d".repeat(64)))))
        assertNotEquals(key, analysisKey(request.copy(initialFen = request.initialFen.replace("0 1", "1 1")), testIdentity))
    }
    @Test fun requestBoundsAndLegalFullHistoryFailClosed() {
        assertFailsWith<IllegalArgumentException> { AnalysisRequest(BoardPosition.START_FEN, listOf("e2e5")).checked() }
        assertFailsWith<IllegalArgumentException> { AnalysisRequest(BoardPosition.START_FEN, List(513) { "e2e4" }).checked() }
        assertFailsWith<IllegalArgumentException> { AnalysisRequest(BoardPosition.START_FEN, originalMove = "d2d5").checked() }
        assertFailsWith<IllegalArgumentException> { BudgetRequest(moveTimeMillis = 3001).checked() }
        assertFailsWith<IllegalArgumentException> { AnalysisRequest(BoardPosition.START_FEN, listOf("e2e4\nquit")).checked() }
    }
    @Test fun adapterResultsAreRecheckedBeforeCachePublication() {
        val request = AnalysisRequest(BoardPosition.START_FEN, originalMove = "e2e4", budget = BudgetRequest(multiPv = 1))
        val good = result(request)
        assertEquals("e4", AnalysisResult.checked(request, testIdentity, good).original!!.lines.single().san.single())
        assertFailsWith<IllegalArgumentException> { AnalysisResult.checked(request, testIdentity, good.copy(original = null)) }
        assertFailsWith<IllegalArgumentException> { AnalysisResult.checked(request, testIdentity, good.copy(rootSide = PieceColor.BLACK)) }
        assertFailsWith<IllegalArgumentException> { AnalysisResult.checked(request, testIdentity, good.copy(terminal = com.openinglab.shared.chess.PositionStatus.CHECKMATE)) }
        val alternatives = good.alternatives!!
        val bad = alternatives.copy(lines = listOf(alternatives.lines.single().copy(san = listOf("Qh5"))))
        assertFailsWith<IllegalArgumentException> { AnalysisResult.checked(request, testIdentity, good.copy(alternatives = bad)) }
    }
    @Test fun providerHonorsNumericAndDateRetryAfterAndSerialSpacing() = runBlocking {
        var time = 0L; val times = mutableListOf<Long>(); var calls = 0
        val backoff = ProviderBackoff(now = { time }, wait = { time += it })
        val reply = backoff.request { times += time; when (++calls) { 1 -> ProviderReply(429, "65"); 2 -> ProviderReply(503, "Thu, 01 Jan 1970 00:02:00 GMT"); else -> ProviderReply(200) } }
        assertEquals(200, reply.status); assertEquals(listOf(0L, 65_000L, 120_000L), times)
        backoff.request { times += time; ProviderReply(200) }
        assertEquals(121_000L, times.last())
    }
    @Test fun providerRetriesAreBoundedAndLongRetryIsNotClamped() = runBlocking {
        var time = 0L; var calls = 0
        val backoff = ProviderBackoff(now = { time }, wait = { time += it })
        assertEquals(429, backoff.request { calls++; ProviderReply(429) }.status); assertEquals(3, calls)
        assertFailsWith<ApiFailure> { backoff.request { ProviderReply(429, "90000") } }
        Unit
    }
    @Test fun admissionHasBoundedBurstAndRecoversWithClock() = runBlocking {
        var time = 0L; val gate = RequestGate { time }
        repeat(5) { gate.admit() }; assertFailsWith<ApiFailure> { gate.admit() }
        time = 1000; gate.admit(); assertFailsWith<ApiFailure> { gate.admit() }
        Unit
    }
    @Test fun serviceRejectsNonLocalDatabaseAndUncontrolledSchema() {
        assertFailsWith<IllegalArgumentException> { PgRepository("jdbc:postgresql://example.org:5432/db", "test", "") }
        assertFailsWith<IllegalArgumentException> { PgRepository("jdbc:postgresql://127.0.0.1:5432/db", "test", "", "public") }
    }
}
