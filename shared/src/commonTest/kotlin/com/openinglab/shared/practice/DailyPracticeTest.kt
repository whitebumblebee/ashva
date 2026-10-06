// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.practice

import kotlin.test.*

class DailyPracticeTest {
    private fun calendar(today: Long) = StudyCalendar({ today * 1000 + 999 }) { it / 1000 }
    @Test fun emptyHistoryHasZeroDaysAndNoInventedStartDate() {
        val week = calendar(20_000).week(emptyList())
        assertEquals(0, week.studyDays); assertEquals(0, week.streak); assertNull(week.firstDay)
    }
    @Test fun mondayWeekDeduplicatesDifferentActivitiesAndExcludesFutureEvents() {
        // Epoch day 4 was Monday, January 5, 1970.
        val week = calendar(6).week(listOf(3000, 4000, 4010, 5000, 6010, 7000))
        assertEquals(4, week.monday); assertEquals(3, week.studyDays); assertEquals(4, week.streak)
        assertEquals(3, week.firstDay)
    }
    @Test fun sundayBelongsToPreviousMondayAndMondayStartsANewWeek() {
        assertEquals(4, calendar(10).week(listOf(10_000)).monday)
        assertEquals(11, calendar(11).week(listOf(10_000, 11_000)).monday)
        assertEquals(1, calendar(11).week(listOf(10_000, 11_000)).studyDays)
    }
    @Test fun streakContinuesFromYesterdayUntilTodayIsStudied() {
        assertEquals(2, calendar(10).week(listOf(8000, 9000)).streak)
        assertEquals(3, calendar(10).week(listOf(8000, 9000, 10_000)).streak)
        assertEquals(0, calendar(11).week(listOf(8000, 9000)).streak)
    }
    @Test fun gapBreaksStreakButDoesNotEraseWeeklyActivity() {
        val week = calendar(10).week(listOf(4000, 5000, 8000, 10_000))
        assertEquals(4, week.studyDays); assertEquals(1, week.streak)
    }
    @Test fun routineCountsAllPuzzleAttemptsAndOnlyCompletedOpeningPracticeToday() {
        val activity = LearnerActivity(openings = listOf(
            StudyActivity("a", "course", "line", StudyActivity.PRACTICE, 5000),
            StudyActivity("b", "course", "line", StudyActivity.STUDY, 5000),
            StudyActivity("c", "course", "line", StudyActivity.PRACTICE, 4000)), due = 4)
        val progress = routineProgress(calendar(5), activity, listOf(5000, 5010, 4000, 6000), RoutineSettings())
        assertEquals(2, progress.puzzles); assertEquals(1, progress.lines); assertEquals(4, progress.due)
        assertTrue(progress.showReview); assertFalse(progress.done)
    }
    @Test fun targetChangesRecomputeCompletionWithoutChangingRecordedCounts() {
        val old = RoutineProgress(10, 3, 0)
        assertFalse(old.done)
        val changed = old.copy(settings = old.settings.copy(dailyPuzzles = 10))
        assertTrue(changed.done); assertEquals(10, changed.puzzles)
        assertFalse(changed.copy(settings = changed.settings.copy(dailyLines = 5)).done)
    }
    @Test fun reviewHidesWhenNothingIsDueAndToggleControlsRoutineCompletion() {
        val progress = RoutineProgress(20, 3, 1)
        assertFalse(progress.done); assertTrue(progress.showReview)
        assertTrue(progress.copy(due = 0).done); assertFalse(progress.copy(due = 0).showReview)
        assertTrue(progress.copy(settings = progress.settings.copy(reviewDue = false)).done)
    }
    @Test fun extraPracticeCountsAreRetainedAfterTargetIsMet() {
        val progress = RoutineProgress(31, 7, 0)
        assertTrue(progress.done); assertEquals(31, progress.puzzles); assertEquals(7, progress.lines)
    }
    @Test fun partialPuzzleAndMistakePracticeAreRealActivityWithoutCountingAsOpeningLines() {
        val activity = LearnerActivity(puzzleActivities = listOf(
            StudyActivity("partial", "easy", "1:puzzle", StudyActivity.PUZZLE, 5000),
            StudyActivity("retry", "easy", "retry:puzzle", StudyActivity.PUZZLE, 5010)))
        val progress = routineProgress(calendar(5), activity, emptyList(), RoutineSettings())
        assertEquals(2, progress.puzzles); assertEquals(0, progress.lines)
    }
    @Test fun injectedClockUpdatesTodayAtCalendarMidnight() {
        var now = 5999L
        val calendar = StudyCalendar({ now }) { it / 1000 }
        assertEquals(1, calendar.countToday(listOf(5000)))
        now = 6000
        assertEquals(0, calendar.countToday(listOf(5000)))
        assertEquals(1, calendar.week(listOf(5000)).streak)
    }
    @Test fun settingsRejectUnsupportedTargets() {
        RoutineSettings().validate()
        assertFailsWith<IllegalArgumentException> { RoutineSettings(dailyPuzzles = 0).validate() }
        assertFailsWith<IllegalArgumentException> { RoutineSettings(dailyLines = 2).validate() }
        assertFailsWith<IllegalArgumentException> { RoutineSettings(weeklyGoal = 8).validate() }
    }
    @Test fun lineProgressIndexesDeduplicateExactLessonsAndKeepStudySeparateFromPractice() {
        val study = StudyActivity("a", "course-v1", "line", StudyActivity.STUDY, 1000)
        val activity = LearnerActivity(openings = listOf(study, study.copy(id = "b"), study.copy(id = "c", lessonId = "course-v2"),
            study.copy(id = "d", kind = StudyActivity.PRACTICE), study.copy(id = "e", kind = StudyActivity.PRACTICE)))
        assertEquals(mapOf("course-v1" to 1, "course-v2" to 1), activity.studiedLineCounts)
        assertEquals(1, activity.practisedLineCount)
        assertEquals(3, activity.copy(openings = activity.openings + study.copy(id = "f", pathId = "other")).studiedLineCounts.values.sum())
    }
}
