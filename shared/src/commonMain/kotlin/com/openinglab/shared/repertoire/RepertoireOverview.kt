// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.repertoire

import com.openinglab.shared.model.PieceColor

enum class RepertoireMemberStatus { AVAILABLE, UNAVAILABLE, CHANGED }
data class RepertoireMemberSummary(
    val policy: RepertoirePolicy,
    val status: RepertoireMemberStatus,
    val fittingRoutes: Int = 0,
    val unansweredBranches: Int = 0,
    val sourceEndpoints: Int = 0,
    val excludedReplies: Int = 0,
)
data class RepertoireChoiceOrigin(val policyId: String, val pathId: String, val ply: Int)
data class ConflictingRepertoireChoice(val uci: String, val san: String, val origins: List<RepertoireChoiceOrigin>)
data class RepertoireConflict(val positionKey: String, val choices: List<ConflictingRepertoireChoice>)
data class RepertoireOverview(
    val side: PieceColor,
    val members: List<RepertoireMemberSummary>,
    val preferredPositions: Int,
    val includedReplies: Int,
    val reachedPositions: Int,
    val conflicts: List<RepertoireConflict>,
) {
    val unavailableMembers: Int get() = members.count { it.status != RepertoireMemberStatus.AVAILABLE }
}

/** A live, read-only aggregation of independently persisted family policies, not a new synthetic book.
 * Call add on a worker, one graph at a time; the result retains no graphs and creates no new routes.
 * Missing versions remain members but cannot contribute validated coverage. */
class RepertoireOverviewBuilder(private val side: PieceColor) {
    private val members = mutableListOf<RepertoireMemberSummary>()
    private val reached = linkedSetOf<String>()
    private val replies = linkedSetOf<Pair<String, String>>()
    private val own = linkedMapOf<String, LinkedHashMap<String, Pair<String, MutableList<RepertoireChoiceOrigin>>>>()

    fun unavailable(policy: RepertoirePolicy, status: RepertoireMemberStatus) {
        require(status != RepertoireMemberStatus.AVAILABLE)
        checkMember(policy)
        members += RepertoireMemberSummary(policy, status)
    }

    fun add(book: RepertoireBook, policy: RepertoirePolicy, checkpoint: () -> Unit = {}) {
        checkMember(policy)
        book.validate(policy)
        val coverage = book.coverage(policy, checkpoint)
        members += RepertoireMemberSummary(policy, RepertoireMemberStatus.AVAILABLE,
            coverage.eligiblePathIds.size, coverage.gaps.size, coverage.sourceBoundaries, coverage.excludedOpponentReplies)
        val origins = linkedMapOf<String, RepertoireChoiceOrigin>()
        for (path in book.graph.paths.values) {
            checkpoint()
            path.positions.forEachIndexed { ply, board ->
                checkpoint()
                if (board.positionKey in coverage.reachedPositionKeys)
                    origins.getOrPut(board.positionKey) { RepertoireChoiceOrigin(policy.id, path.id, ply) }
            }
        }
        for (key in coverage.reachedPositionKeys) {
            checkpoint()
            reached += key
            policy.preferredMoves[key]?.let { uci ->
                val option = book.optionsAt(key).single { it.uci == uci }
                own.getOrPut(key) { linkedMapOf() }.getOrPut(uci) { option.san to mutableListOf() }
                    .second += origins.getValue(key)
            }
            policy.opponentReplies[key].orEmpty().forEach { replies += key to it }
            require(reached.size + replies.size <= 100_000) { "Combined scope exceeds the local overview limit; no choices were silently dropped" }
        }
    }

    private fun checkMember(policy: RepertoirePolicy) {
        require(policy.side == side) { "White and Black repertoires must stay separate" }
        require(members.size < 256) { "Too many saved families; no members were silently dropped" }
        require(members.none { it.policy.id == policy.id }) { "Duplicate policy identity" }
    }

    fun build(checkpoint: () -> Unit = {}): RepertoireOverview = RepertoireOverview(side, members.toList(), own.size, replies.size, reached.size,
        own.entries.asSequence().onEach { checkpoint() }.filter { it.value.size > 1 }.map { (key, choices) -> RepertoireConflict(key,
            choices.map { (uci, value) -> ConflictingRepertoireChoice(uci, value.first, value.second.toList()) }) }.toList())
}
