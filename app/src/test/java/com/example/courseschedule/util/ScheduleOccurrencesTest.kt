package com.example.courseschedule.util

import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.db.entity.ScheduleChange
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleOccurrencesTest {
    @Test
    fun movingOneOddWeekClassKeepsTheOtherWeeks() {
        val monday = LocalDate.of(2026, 9, 7)
        val saturday = monday.plusDays(5)
        val schedule = Schedule(1, 1, 1, 1, 2, 1, 4, weekType = 1)
        val moved = ScheduleChange(1, monday.toEpochDay(), saturday.toEpochDay(), 3, 4, "B201")
        val schedules = listOf(schedule)
        val changes = listOf(moved)

        assertEquals(0, scheduleOccurrences(monday, monday, 4, schedules, changes).size)
        assertEquals(3, scheduleOccurrences(saturday, monday, 4, schedules, changes).single().schedule.startPeriod)
        assertEquals(0, scheduleOccurrences(monday.plusWeeks(1), monday, 4, schedules, changes).size)
        assertEquals(schedule, scheduleOccurrences(monday.plusWeeks(2), monday, 4, schedules, changes).single().schedule)
    }
}
