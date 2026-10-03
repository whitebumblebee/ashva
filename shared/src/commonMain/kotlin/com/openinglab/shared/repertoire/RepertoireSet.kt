// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.repertoire

import com.openinglab.shared.model.PieceColor
import kotlinx.serialization.Serializable

/** Exact revisions, not mutable pointers: changing a family never silently changes a saved set. */
@Serializable data class RepertoirePolicyRef(val id: String, val revision: Int)
@Serializable data class RepertoireSet(
    val id: String,
    val name: String,
    val side: PieceColor,
    val members: List<RepertoirePolicyRef>,
    val revision: Int = 0,
    val schemaVersion: Int = 1,
) {
    fun validate() {
        require(schemaVersion == 1 && revision >= 0)
        require(id.length in 1..512 && name.isNotBlank() && name.length <= 120 && name.none { it.isISOControl() })
        require(members.size in 1..64 && members.map { it.id }.distinct().size == members.size)
        require(members.all { it.id.length in 1..512 && it.revision >= 0 })
    }
}

data class RepertoirePracticeItem(val policy: RepertoirePolicyRef, val lessonId: String, val pathId: String, val title: String)
data class RepertoireSetPlan(val set: RepertoireSet, val overview: RepertoireOverview, val items: List<RepertoirePracticeItem>) {
    val ready: Boolean get() = overview.unavailableMembers == 0 && overview.conflicts.isEmpty() &&
        overview.members.all { it.fittingRoutes > 0 } && items.isNotEmpty()
}
data class RepertoireSetSession(val plan: RepertoireSetPlan, val index: Int) {
    init { require(plan.ready && index in plan.items.indices) }
    val item: RepertoirePracticeItem get() = plan.items[index]
}

/** Build on a worker, one immutable family graph at a time. Never stitch unsupported move orders.
 * Interleave admitted routes across families; repeated named routes retain their own provenance.
 * Incompatible preferred choices prevent unified practice, not separate family study. */
class RepertoireSetPlanner(private val set: RepertoireSet) {
    private val overview = RepertoireOverviewBuilder(set.side)
    private val routes = linkedMapOf<String, List<RepertoirePracticeItem>>()
    private val seen = mutableSetOf<String>()
    init { set.validate() }

    private fun check(ref: RepertoirePolicyRef, policy: RepertoirePolicy) {
        require(ref in set.members && ref.id == policy.id && ref.revision == policy.revision && seen.add(ref.id))
        require(policy.side == set.side)
    }

    fun add(book: RepertoireBook, policy: RepertoirePolicy, checkpoint: () -> Unit = {}) {
        val ref = RepertoirePolicyRef(policy.id, policy.revision)
        check(ref, policy)
        overview.add(book, policy, checkpoint)
        val coverage = book.coverage(policy, checkpoint)
        routes[ref.id] = coverage.eligiblePathIds.sorted().map { id ->
            checkpoint()
            val path = book.graph.paths.getValue(id)
            RepertoirePracticeItem(ref, policy.lessonId, id, "${book.graph.title} · ${path.name}")
        }
        require(routes.values.sumOf { it.size } <= MAX_ITEMS) { "Practice queue exceeds local limits; no routes were dropped" }
    }

    fun unavailable(policy: RepertoirePolicy, status: RepertoireMemberStatus) {
        check(RepertoirePolicyRef(policy.id, policy.revision), policy)
        overview.unavailable(policy, status)
        routes[policy.id] = emptyList()
    }

    fun build(checkpoint: () -> Unit = {}): RepertoireSetPlan {
        require(seen == set.members.map { it.id }.toSet()) { "A set member was not checked; no silent omission is allowed" }
        val groups = set.members.map { routes.getValue(it.id) }
        val items = buildList {
            repeat(groups.maxOfOrNull { it.size } ?: 0) { index ->
                for (group in groups) { checkpoint(); group.getOrNull(index)?.let { add(it) } }
            }
        }
        return RepertoireSetPlan(set, overview.build(checkpoint), items)
    }

    companion object { const val MAX_ITEMS = 10_000 }
}
