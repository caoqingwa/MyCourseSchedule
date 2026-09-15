package com.example.courseschedule.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** 周次/节次时间等日期逻辑的纯 JVM 单测（不依赖 Android API） */
class DateUtilsTest {

    private fun monday(): Long {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 4, 9, 30, 0) // 任意一个周三
            set(Calendar.MILLISECOND, 0)
        }
        return DateUtils.getStartOfWeek(cal.timeInMillis)
    }

    @Test
    fun weekNumberSameWeekIsOne() {
        val start = monday()
        assertEquals(1, DateUtils.getWeekNumber(start + 2L * 24 * 3600_000, start))
    }

    @Test
    fun weekNumberNextMondayIsTwo() {
        val start = monday()
        assertEquals(2, DateUtils.getWeekNumber(start + 7L * 24 * 3600_000, start))
    }

    @Test
    fun scheduleActiveRespectsWeekRange() {
        assertTrue(DateUtils.isScheduleActive(1, 16, 0, 1))
        assertTrue(DateUtils.isScheduleActive(1, 16, 0, 16))
        assertFalse(DateUtils.isScheduleActive(1, 16, 0, 17))
        assertFalse(DateUtils.isScheduleActive(2, 16, 0, 1))
    }

    @Test
    fun scheduleActiveRespectsOddEvenWeeks() {
        assertTrue(DateUtils.isScheduleActive(1, 16, 1, 3))  // 单周
        assertFalse(DateUtils.isScheduleActive(1, 16, 1, 4))
        assertTrue(DateUtils.isScheduleActive(1, 16, 2, 4))  // 双周
        assertFalse(DateUtils.isScheduleActive(1, 16, 2, 3))
    }

    @Test
    fun periodTimeRangeFallsBackToDefaultWithoutSemester() {
        assertEquals("08:00-08:45", DateUtils.getPeriodTimeRange(1, 1, null))
        assertEquals("21:45-22:30", DateUtils.getPeriodTimeRange(12, 12, null))
    }
}
