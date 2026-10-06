// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.tactics

import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.Flow

@Serializable
data class TacticsCustomSet(val id: String, val spec: CustomSetSpec, val puzzleIds: List<String>, val createdAt: Long) {
    fun asSet() = PuzzleSet(id, spec.name, puzzleIds, "CUSTOM")
}
@Serializable
data class TacticsCycle(val setId: String, val cycle: Int, val startedAt: Long, val completedAt: Long? = null, val activeMs: Long = 0)
@Serializable
data class TacticsAttempt(val setId: String, val cycle: Int, val puzzleId: String, val ordinal: Int,
    val correct: Boolean, val activeMs: Long, val at: Long)
data class CycleStats(val activeMs: Long, val accuracy: Double, val averageSeconds: Double, val improvement: Double?, val targetMs: Long?)

object Woodpecker {
    const val RECOMMENDED_CYCLES = 7
    fun targetMs(puzzleCount: Int, previous: TacticsCycle?): Long? {
        require(puzzleCount > 0)
        if (previous == null) return null
        require(previous.completedAt != null && previous.activeMs >= 0)
        return maxOf(previous.activeMs / 2, 15_000L * puzzleCount / 4, 5_000L * puzzleCount)
    }
    fun nextOrdinal(ids: List<String>, attempts: List<TacticsAttempt>): Int {
        require(ids.isNotEmpty() && ids.distinct().size == ids.size)
        val ordered = attempts.sortedBy { it.ordinal }
        require(ordered.map { it.puzzleId }.distinct().size == ordered.size)
        ordered.forEachIndexed { index, attempt ->
            require(attempt.ordinal == index && ids.getOrNull(index) == attempt.puzzleId && attempt.activeMs >= 0)
        }
        return ordered.size
    }
    fun complete(ids: List<String>, attempts: List<TacticsAttempt>) = nextOrdinal(ids, attempts) == ids.size
    fun stats(cycle: TacticsCycle, attempts: List<TacticsAttempt>, count: Int, previous: TacticsCycle?): CycleStats {
        require(count > 0 && cycle.activeMs >= 0)
        require(attempts.all { it.setId == cycle.setId && it.cycle == cycle.cycle })
        require(attempts.map { it.puzzleId }.distinct().size == attempts.size && attempts.size <= count)
        val accuracy = if (attempts.isEmpty()) 0.0 else attempts.count { it.correct }.toDouble() / attempts.size
        return CycleStats(cycle.activeMs, accuracy, cycle.activeMs / 1000.0 / count,
            previous?.takeIf { it.completedAt != null && it.activeMs > 0 }?.let { 1.0 - cycle.activeMs.toDouble() / it.activeMs }, targetMs(count, previous))
    }
}

data class TacticsHistory(val customSets: List<TacticsCustomSet> = emptyList(), val cycles: List<TacticsCycle> = emptyList(),
    val attempts: List<TacticsAttempt> = emptyList()) {
    val cyclesBySet by lazy { cycles.groupBy { it.setId }.mapValues { (_, values) -> values.sortedBy { it.cycle } } }
    val attemptsByCycle by lazy { attempts.groupBy { it.setId to it.cycle }.mapValues { (_, values) -> values.sortedBy { it.ordinal } } }
    val latestCycle by lazy {
        val latestAttempts = attempts.groupBy { it.setId }.mapValues { (_, values) -> values.maxOf { it.at } }
        cycles.maxByOrNull { maxOf(it.startedAt, latestAttempts[it.setId] ?: 0) }
    }
}

/** First attempts and time updates are atomic; mistakes practice never writes to this store. */
interface TacticsStore {
    val history: Flow<TacticsHistory>
    suspend fun load(): TacticsHistory
    suspend fun createCustomSet(value: TacticsCustomSet)
    suspend fun startCycle(setId: String, at: Long): TacticsCycle
    suspend fun checkpoint(setId: String, cycle: Int, activeMs: Long)
    suspend fun recordAttempt(value: TacticsAttempt, puzzleIds: List<String>, cycleActiveMs: Long)
    suspend fun reset(setId: String)
}
