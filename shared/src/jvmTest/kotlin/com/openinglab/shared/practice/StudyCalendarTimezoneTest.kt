// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.practice

import java.time.*
import kotlin.test.*

class StudyCalendarTimezoneTest {
    private fun time(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun calendar(now: String, timezone: String) = StudyCalendar({ time(now) }) {
        Instant.ofEpochMilli(it).atZone(ZoneId.of(timezone)).toLocalDate().toEpochDay()
    }
    @Test fun sameUtcDateCanBeTwoLocalStudyDays() {
        val calendar = calendar("2026-10-06T20:00:00Z", "Asia/Kolkata")
        val times = listOf(time("2026-10-06T18:29:00Z"), time("2026-10-06T18:31:00Z"))
        assertEquals(2, calendar.week(times).studyDays)
        assertEquals(2, calendar.week(times).streak)
        assertEquals(1, calendar.countToday(times))
    }
    @Test fun springForwardDayIsACalendarDayEvenThoughItLasts23Hours() {
        val calendar = calendar("2026-03-09T04:30:00Z", "America/New_York")
        val times = listOf(time("2026-03-08T05:01:00Z"), time("2026-03-09T03:59:00Z"), time("2026-03-09T04:01:00Z"))
        assertEquals(2, calendar.week(times).activeDays.size)
        assertEquals(2, calendar.week(times).streak)
        assertEquals(1, calendar.countToday(times))
    }
    @Test fun fallBackRepeatedHourDoesNotInventASecondStudyDay() {
        val calendar = calendar("2026-11-01T07:00:00Z", "America/New_York")
        val times = listOf(time("2026-11-01T05:30:00Z"), time("2026-11-01T06:30:00Z"))
        assertEquals(1, calendar.week(times).studyDays)
        assertEquals(1, calendar.week(times).streak)
        assertEquals(2, calendar.countToday(times))
    }
    @Test fun weekCrossesYearBoundaryUsingLocalMonday() {
        val calendar = calendar("2027-01-03T10:00:00Z", "Pacific/Auckland")
        val week = calendar.week(listOf(time("2026-12-28T02:00:00Z"), time("2027-01-02T12:00:00Z")))
        assertEquals(LocalDate.of(2026, 12, 28).toEpochDay(), week.monday)
        assertEquals(2, week.studyDays)
    }
}
