// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.practice

data class RoutineSettings(val dailyPuzzles: Int = 20, val dailyLines: Int = 3,
    val reviewDue: Boolean = true, val weeklyGoal: Int = 5) {
    fun validate() {
        require(dailyPuzzles in listOf(10, 20, 30, 50))
        require(dailyLines in listOf(1, 3, 5))
        require(weeklyGoal in 3..7)
    }
}

/** An immutable activity event, separate from recall grades and retained across content changes. */
data class StudyActivity(val id: String, val lessonId: String, val pathId: String,
    val kind: String, val recordedAt: Long) {
    companion object { const val STUDY = "STUDY"; const val PRACTICE = "PRACTICE"; const val PUZZLE = "PUZZLE" }
}
data class LearnerActivity(val timestamps: List<Long> = emptyList(),
    val openings: List<StudyActivity> = emptyList(), val due: Int = 0,
    val puzzleActivities: List<StudyActivity> = emptyList()) {
    val studiedLineCounts: Map<String, Int> by lazy {
        openings.asSequence().filter { it.kind == StudyActivity.STUDY }.map { it.lessonId to it.pathId }
            .distinct().groupingBy { it.first }.eachCount()
    }
    val practisedLineCount: Int by lazy {
        openings.asSequence().filter { it.kind == StudyActivity.PRACTICE }.map { it.lessonId to it.pathId }.distinct().count()
    }
}

data class StudyWeek(val today: Long, val monday: Long, val activeDays: Set<Long>,
    val streak: Int, val firstDay: Long?) {
    val studyDays: Int get() = (0L..6L).count { monday + it in activeDays }
}

/** The platform supplies calendar conversion, so DST and the user's time zone are respected.
 * Clock and calendar are injected; no 24-hour-duration approximation of a local day is used. */
class StudyCalendar(private val clock: () -> Long, private val localDay: (Long) -> Long) {
    fun week(timestamps: List<Long>): StudyWeek {
        val now = clock()
        val today = localDay(now)
        val days = timestamps.asSequence().filter { it in 0..now }.map(localDay).toSet()
        val weekday = ((today + 3) % 7 + 7) % 7 // 1970-01-01 was Thursday; Monday = 0.
        val monday = today - weekday
        var cursor = if (today in days) today else today - 1
        var streak = 0
        while (cursor in days) { streak++; cursor-- }
        return StudyWeek(today, monday, days, streak, days.minOrNull())
    }
    fun countToday(timestamps: List<Long>): Int {
        val now = clock(); val today = localDay(now)
        return timestamps.count { it in 0..now && localDay(it) == today }
    }
}

data class RoutineProgress(val puzzles: Int = 0, val lines: Int = 0, val due: Int = 0,
    val settings: RoutineSettings = RoutineSettings()) {
    val tacticsDone: Boolean get() = puzzles >= settings.dailyPuzzles
    val openingsDone: Boolean get() = lines >= settings.dailyLines
    val showReview: Boolean get() = settings.reviewDue && due > 0
    val done: Boolean get() = tacticsDone && openingsDone && !showReview
}

fun routineProgress(calendar: StudyCalendar, activity: LearnerActivity, puzzleAttemptTimes: List<Long>,
    settings: RoutineSettings): RoutineProgress = RoutineProgress(
    calendar.countToday(puzzleAttemptTimes + activity.puzzleActivities.map { it.recordedAt }),
    calendar.countToday(activity.openings.filter { it.kind == StudyActivity.PRACTICE }.map { it.recordedAt }),
    activity.due, settings)
