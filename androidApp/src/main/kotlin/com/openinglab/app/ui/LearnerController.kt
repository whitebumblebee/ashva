// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui

import com.openinglab.app.content.AppPreferences
import com.openinglab.app.content.LearnerPreferences
import com.openinglab.shared.practice.*
import com.openinglab.shared.storage.LearningStore
import com.openinglab.shared.tactics.TacticsHistory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.ZoneId

data class LearnerUiState(val preferences: LearnerPreferences = LearnerPreferences(),
    val week: StudyWeek = StudyWeek(0, 0, emptySet(), 0, null),
    val routine: RoutineProgress = RoutineProgress(), val activity: LearnerActivity = LearnerActivity(),
    val tactics: TacticsHistory = TacticsHistory(), val hour: Int = 12, val loading: Boolean = true,
    val error: String? = null) {
    val practisedLines: Int get() = activity.practisedLineCount
    val studiedLines: Int get() = activity.studiedLineCounts.values.sum()
    fun studiedIn(lessonIds: Set<String>): Int = lessonIds.sumOf { activity.studiedLineCounts[it] ?: 0 }
}

/** Independently loads stored activity even when a content pack is unavailable. */
@OptIn(ExperimentalCoroutinesApi::class)
class LearnerController(store: LearningStore?, scope: CoroutineScope, private val preferences: AppPreferences?,
    private val clock: () -> Long = System::currentTimeMillis, private val zone: () -> ZoneId = ZoneId::systemDefault) {
    private val settings = MutableStateFlow(preferences?.learner() ?: LearnerPreferences())
    private val mutable = MutableStateFlow(LearnerUiState(preferences = settings.value))
    val state = mutable.asStateFlow()
    init {
        scope.launch {
            val ticks = flow { while (currentCoroutineContext().isActive) { emit(clock()); delay(30_000) } }
            try {
                combine(settings, ticks.flatMapLatest { at -> store?.learnerActivity(at) ?: flowOf(LearnerActivity()) },
                    store?.tactics?.history ?: flowOf(TacticsHistory()), ticks) { prefs, activity, tactics, _ ->
                    // Warm immutable activity indexes on this flow's Default dispatcher, once per snapshot.
                    activity.studiedLineCounts; activity.practisedLineCount
                    val at = clock()
                    val timezone = zone()
                    val calendar = StudyCalendar({ at }) { Instant.ofEpochMilli(it).atZone(timezone).toLocalDate().toEpochDay() }
                    val loggedPuzzles = activity.puzzleActivities.map { Triple(it.lessonId, it.pathId,
                        Instant.ofEpochMilli(it.recordedAt).atZone(timezone).toLocalDate()) }.toSet()
                    val legacyTimes = tactics.attempts.filterNot { Triple(it.setId, "${it.cycle}:${it.puzzleId}",
                        Instant.ofEpochMilli(it.at).atZone(timezone).toLocalDate()) in loggedPuzzles }.map { it.at }
                    LearnerUiState(prefs, calendar.week(activity.timestamps + tactics.attempts.map { it.at }),
                        routineProgress(calendar, activity, legacyTimes, prefs.routine), activity, tactics,
                        Instant.ofEpochMilli(at).atZone(timezone).hour, loading = false)
                }.flowOn(Dispatchers.Default).collect { mutable.value = it }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(loading = false, error = "Activity could not be loaded. Your history is retained.") }
            }
        }
    }
    fun update(value: LearnerPreferences) {
        value.routine.validate()
        val cleaned = value.copy(displayName = value.displayName.trim().take(40))
        preferences?.save(cleaned)
        settings.value = cleaned
        mutable.update { it.copy(preferences = cleaned) }
    }
}
