// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.storage

import androidx.room.*
import com.openinglab.shared.tactics.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "tactics_custom_sets")
data class TacticsCustomSetEntity(@PrimaryKey val id: String, val name: String, val specJson: String,
    val puzzleIdsJson: String, val createdAt: Long)
@Entity(tableName = "tactics_cycles", primaryKeys = ["setId", "cycle"])
data class TacticsCycleEntity(val setId: String, val cycle: Int, val startedAt: Long, val completedAt: Long?, val activeMs: Long)
@Entity(tableName = "tactics_attempts", primaryKeys = ["setId", "cycle", "puzzleId"])
data class TacticsAttemptEntity(val setId: String, val cycle: Int, val puzzleId: String, val ordinal: Int,
    val correct: Boolean, val activeMs: Long, val at: Long)

@Dao
interface TacticsDao {
    @Query("SELECT * FROM tactics_custom_sets ORDER BY createdAt, id") fun customSets(): Flow<List<TacticsCustomSetEntity>>
    @Query("SELECT * FROM tactics_cycles ORDER BY setId, cycle") fun cycles(): Flow<List<TacticsCycleEntity>>
    @Query("SELECT * FROM tactics_attempts ORDER BY setId, cycle, ordinal") fun attempts(): Flow<List<TacticsAttemptEntity>>
    @Query("SELECT * FROM tactics_custom_sets ORDER BY createdAt, id") suspend fun allCustomSets(): List<TacticsCustomSetEntity>
    @Query("SELECT * FROM tactics_cycles ORDER BY setId, cycle") suspend fun allCycles(): List<TacticsCycleEntity>
    @Query("SELECT * FROM tactics_attempts ORDER BY setId, cycle, ordinal") suspend fun allAttempts(): List<TacticsAttemptEntity>
    @Query("SELECT * FROM tactics_custom_sets WHERE id = :id") suspend fun customSet(id: String): TacticsCustomSetEntity?
    @Query("SELECT * FROM tactics_cycles WHERE setId = :id ORDER BY cycle DESC LIMIT 1") suspend fun latest(id: String): TacticsCycleEntity?
    @Query("SELECT * FROM tactics_cycles WHERE setId = :id AND cycle = :cycle") suspend fun cycle(id: String, cycle: Int): TacticsCycleEntity?
    @Query("SELECT * FROM tactics_attempts WHERE setId = :id AND cycle = :cycle ORDER BY ordinal") suspend fun cycleAttempts(id: String, cycle: Int): List<TacticsAttemptEntity>
    @Insert suspend fun customSet(value: TacticsCustomSetEntity)
    @Upsert suspend fun cycle(value: TacticsCycleEntity)
    @Insert suspend fun attempt(value: TacticsAttemptEntity)
    @Query("DELETE FROM tactics_attempts WHERE setId = :id") suspend fun deleteAttempts(id: String)
    @Query("DELETE FROM tactics_cycles WHERE setId = :id") suspend fun deleteCycles(id: String)
}

/** Shares the learning database's writer and never changes opening, recall, or source rows. */
class RoomTacticsStore(private val database: LearningDatabase) : TacticsStore {
    private val dao = database.tacticsDao()
    private val json = Json { encodeDefaults = true }
    private fun TacticsCustomSetEntity.model(): TacticsCustomSet {
        val spec = json.decodeFromString<CustomSetSpec>(specJson).also { it.validate() }
        require(name == spec.name)
        return TacticsCustomSet(id, spec, json.decodeFromString(puzzleIdsJson), createdAt)
    }
    private fun TacticsCycleEntity.model() = TacticsCycle(setId, cycle, startedAt, completedAt, activeMs)
    private fun TacticsAttemptEntity.model() = TacticsAttempt(setId, cycle, puzzleId, ordinal, correct, activeMs, at)
    override val history: Flow<TacticsHistory> = combine(dao.customSets(), dao.cycles(), dao.attempts()) { sets, cycles, attempts ->
        TacticsHistory(sets.map { it.model() }, cycles.map { it.model() }, attempts.map { it.model() })
    }
    override suspend fun load(): TacticsHistory = database.useWriterConnection { it.immediateTransaction {
        TacticsHistory(dao.allCustomSets().map { it.model() }, dao.allCycles().map { it.model() }, dao.allAttempts().map { it.model() })
    } }
    override suspend fun createCustomSet(value: TacticsCustomSet) {
        value.spec.validate()
        require(value.id.startsWith("custom-") && value.id.length <= 80)
        require(value.puzzleIds.size == value.spec.size && value.puzzleIds.distinct().size == value.puzzleIds.size)
        database.useWriterConnection { it.immediateTransaction {
            val old = dao.customSet(value.id)
            require(old == null || old.model() == value) { "A custom set is immutable" }
            if (old == null) dao.customSet(TacticsCustomSetEntity(value.id, value.spec.name, json.encodeToString(value.spec), json.encodeToString(value.puzzleIds), value.createdAt))
        } }
    }
    override suspend fun startCycle(setId: String, at: Long): TacticsCycle = database.useWriterConnection { it.immediateTransaction {
        require(setId.isNotBlank() && setId.length <= 80 && at >= 0)
        val last = dao.latest(setId)
        if (last != null && last.completedAt == null) last.model() else {
            val next = TacticsCycleEntity(setId, (last?.cycle ?: 0) + 1, at, null, 0)
            dao.cycle(next); next.model()
        }
    } }
    override suspend fun checkpoint(setId: String, cycle: Int, activeMs: Long) {
        require(activeMs >= 0)
        database.useWriterConnection { it.immediateTransaction {
            val old = requireNotNull(dao.cycle(setId, cycle))
            if (old.completedAt == null && activeMs > old.activeMs) dao.cycle(old.copy(activeMs = activeMs))
        } }
    }
    override suspend fun recordAttempt(value: TacticsAttempt, puzzleIds: List<String>, cycleActiveMs: Long) {
        require(value.activeMs >= 0 && cycleActiveMs >= value.activeMs && value.at >= 0)
        database.useWriterConnection { it.immediateTransaction {
            val cycle = requireNotNull(dao.cycle(value.setId, value.cycle))
            val old = dao.cycleAttempts(value.setId, value.cycle).map { row -> row.model() }
            val duplicate = old.firstOrNull { row -> row.puzzleId == value.puzzleId }
            if (duplicate != null) { require(duplicate == value) { "First attempt cannot be overwritten" }; return@immediateTransaction }
            check(cycle.completedAt == null)
            val ordinal = Woodpecker.nextOrdinal(puzzleIds, old)
            require(value.ordinal == ordinal && puzzleIds.getOrNull(ordinal) == value.puzzleId)
            dao.attempt(TacticsAttemptEntity(value.setId, value.cycle, value.puzzleId, value.ordinal, value.correct, value.activeMs, value.at))
            val active = maxOf(cycle.activeMs, cycleActiveMs, old.sumOf { row -> row.activeMs } + value.activeMs)
            dao.cycle(cycle.copy(activeMs = active, completedAt = value.at.takeIf { ordinal + 1 == puzzleIds.size }))
        } }
    }
    override suspend fun reset(setId: String) = database.useWriterConnection { it.immediateTransaction {
        dao.deleteAttempts(setId); dao.deleteCycles(setId)
    } }
}
