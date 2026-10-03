// SPDX-License-Identifier: Apache-2.0
package com.openinglab.service

import com.openinglab.shared.analysis.*
import com.openinglab.shared.chess.PositionStatus
import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.content.ContentManifest
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.storage.contentSha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal val codec = Json { encodeDefaults = true; ignoreUnknownKeys = false }
internal fun hash(text: String) = contentSha256(text.encodeToByteArray())
internal val hashPattern = Regex("[a-f0-9]{64}")
class ApiFailure(val code: String, val httpStatus: Int = 400) : IllegalArgumentException(code)
@Serializable data class ApiError(val code: String)
@Serializable data class PackDescriptor(val manifestSha256: String, val manifest: ContentManifest,
    val manifestUrl: String = "/v1/packs/$manifestSha256/manifest.json")
@Serializable data class Catalog(val version: String, val packs: List<PackDescriptor>)
@Serializable data class CatalogDelta(val from: String, val to: String, val changed: List<PackDescriptor>,
    val inactiveSourceIds: List<String>)
@Serializable data class ContentItem(val packSha256: String, val recordId: String, val payload: JsonElement)
@Serializable data class ContentPage(val version: String, val sources: List<PackDescriptor>,
    val items: List<ContentItem>, val nextCursor: String?)
@Serializable internal data class PageCursor(val version: String, val kind: String, val query: String,
    val limit: Int, val lastPack: String, val lastRecord: String)
@Serializable data class ImportRequest(val packId: String)
@Serializable data class BudgetRequest(val depth: Int = 16, val nodes: Int = 50_000,
    val moveTimeMillis: Int = 1000, val multiPv: Int = 3, val hashMiB: Int = 16) {
    fun checked() = AnalysisBudget(depth, nodes, moveTimeMillis, multiPv, hashMiB)
}
@Serializable data class AnalysisRequest(val initialFen: String, val moves: List<String> = emptyList(),
    val originalMove: String? = null, val budget: BudgetRequest = BudgetRequest()) {
    fun checked(): AnalysisPosition {
        require(initialFen.length in 1..120 && moves.size <= AnalysisPosition.MAX_HISTORY_PLIES)
        require(moves.all { it.matches(Regex("[a-h][1-8][a-h][1-8][qrbn]?")) })
        val position = AnalysisPosition(initialFen, moves)
        val board = position.board()
        require(board.toFen().length <= 120)
        originalMove?.let { require(board.isLegal(ChessMove.fromUci(it))) }
        budget.checked()
        return position
    }
}
@Serializable data class EngineSource(val name: String, val version: String, val binarySha256: String,
    val networks: Map<String, String>, val license: String = "GPL-3.0-or-later") {
    fun identity() = EngineIdentity(name, version, binarySha256, networks)
    companion object { fun from(value: EngineIdentity) = EngineSource(value.name, value.version,
        value.binarySha256, value.networks.toSortedMap()) }
}
@Serializable data class LineResult(val rank: Int, val depth: Int, val nodes: Long, val timeMillis: Long,
    val scoreUnit: String, val score: Int, val bound: String, val uci: List<String>, val san: List<String>)
@Serializable data class SearchResult(val lines: List<LineResult>, val bestMove: String,
    val requestedCandidates: Int, val completeCandidateSet: Boolean)
@Serializable data class AnalysisResult(val request: AnalysisRequest, val engine: EngineSource,
    val rootSide: String, val alternatives: SearchResult?, val original: SearchResult?, val terminal: String?,
    val protocolVersion: Int = 1,
    val qualification: String = "Bounded engine candidates, not expert annotations, guaranteed wins or GM intent.") {
    companion object {
        /** Recheck injected adapters too: malformed or mismatched results never enter the cache. */
        fun checked(request: AnalysisRequest, identity: EngineIdentity, value: EngineAnalysis): AnalysisResult {
            val position = request.checked(); val root = position.board(); val status = root.status()
            require(value.position == position && value.budget == request.budget.checked() && value.engine == identity && value.rootSide == root.sideToMove)
            val terminal = status !in listOf(PositionStatus.ONGOING, PositionStatus.CHECK)
            if (terminal) require(value.terminal == status && value.alternatives == null && value.original == null)
            else require(value.terminal == null && value.alternatives != null && (value.original != null) == (request.originalMove != null))
            fun checkedSearch(search: AnalysisSearch?, original: String?): SearchResult? = search?.let {
                val expected = if (original != null) 1 else minOf(request.budget.multiPv, root.legalMoves().size)
                require(it.requestedCandidates == expected && it.lines.size in 1..expected)
                require(it.lines.map { line -> line.rank } == (1..it.lines.size).toList())
                require(it.completeCandidateSet == (it.lines.size == expected) && it.bestMove == it.lines.first().uci.firstOrNull())
                require(it.lines.map { line -> line.uci.firstOrNull() }.distinct().size == it.lines.size)
                val lines = it.lines.map { line ->
                    require(line.depth in 1..128 && line.nodes >= 0 && line.timeMillis >= 0 && line.uci.size in 1..256 && line.uci.size == line.san.size)
                    require(original == null || line.uci.first() == original)
                    var board = root
                    line.uci.forEachIndexed { i, move ->
                        require(board.status() in listOf(PositionStatus.ONGOING, PositionStatus.CHECK))
                        val step = board.sanAndPlay(ChessMove.fromUci(move)); require(step.san == line.san[i]); board = step.position
                    }
                    val score = when (val score = line.score) {
                        is EngineScore.Centipawns -> "cp" to score.value
                        is EngineScore.Mate -> "mate" to score.moves.also { require(it != 0) }
                    }
                    require(score.second in -100_000..100_000)
                    LineResult(line.rank, line.depth, line.nodes, line.timeMillis, score.first, score.second,
                        line.score.bound.name, line.uci, line.san)
                }
                SearchResult(lines, it.bestMove, expected, it.completeCandidateSet)
            }
            return AnalysisResult(request, EngineSource.from(identity), root.sideToMove.name,
                checkedSearch(value.alternatives, null), checkedSearch(value.original, request.originalMove), value.terminal?.name)
        }
    }
}
@Serializable data class JobView(val id: String, val kind: String, val status: String, val attempts: Int,
    val availableAt: Long, val expiresAt: Long?, val errorCode: String?, val result: JsonElement?, val cached: Boolean = false)
@Serializable data class Metrics(val jobs: Map<String, Long>, val attempts: Long, val workerConcurrency: Int = 1,
    val maxAttempts: Int = 3, val maxJobs: Int = 1000)
internal fun analysisKey(request: AnalysisRequest, engine: EngineIdentity) = hash(
    "ashva-analysis/1\n${codec.encodeToString(request)}\n${codec.encodeToString(EngineSource.from(engine))}")
@Serializable internal data class AnalysisJobRequest(val request: AnalysisRequest, val engine: EngineSource,
    val protocolVersion: Int = 1)
