// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.content

import android.content.SharedPreferences
import androidx.core.content.edit
import com.openinglab.shared.practice.RoutineSettings

data class LearnerPreferences(val displayName: String = "", val routine: RoutineSettings = RoutineSettings(),
    val boardCoordinates: Boolean = true, val playbackMillis: Long = 1200, val puzzlesAutoNext: Boolean = true)

/** App settings live apart from the learner database. */
class AppPreferences(private val preferences: SharedPreferences) {
    fun learner(): LearnerPreferences = LearnerPreferences(
        preferences.getString("displayName", "").orEmpty(),
        RoutineSettings(preferences.getInt("dailyPuzzles", 20).takeIf { it in listOf(10, 20, 30, 50) } ?: 20,
            preferences.getInt("dailyLines", 3).takeIf { it in listOf(1, 3, 5) } ?: 3,
            preferences.getBoolean("reviewDue", true), preferences.getInt("weeklyGoal", 5).coerceIn(3, 7)),
        preferences.getBoolean("boardCoordinates", true),
        preferences.getLong("playbackMillis", 1200).takeIf { it in listOf(700L, 1200L, 2000L) } ?: 1200,
        preferences.getBoolean("puzzlesAutoNext", true))

    fun save(value: LearnerPreferences) {
        value.routine.validate()
        require(value.playbackMillis in listOf(700L, 1200L, 2000L))
        preferences.edit {
            putString("displayName", value.displayName.trim().take(40))
            putInt("dailyPuzzles", value.routine.dailyPuzzles); putInt("dailyLines", value.routine.dailyLines)
            putBoolean("reviewDue", value.routine.reviewDue); putInt("weeklyGoal", value.routine.weeklyGoal)
            putBoolean("boardCoordinates", value.boardCoordinates); putLong("playbackMillis", value.playbackMillis)
            putBoolean("puzzlesAutoNext", value.puzzlesAutoNext)
        }
    }
    var developerMode: Boolean
        get() = preferences.getBoolean("developerMode", false)
        set(value) { preferences.edit { putBoolean("developerMode", value) } }
}
