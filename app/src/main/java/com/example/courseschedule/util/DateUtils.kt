package com.example.courseschedule.util

import java.util.*

object DateUtils {
    private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

    fun getWeekNumber(dateMillis: Long, semesterStartMillis: Long): Int {
        val daysDiff = (dateMillis - semesterStartMillis) / MILLIS_PER_DAY
        return (daysDiff / 7).toInt() + 1
    }

    fun getDayOfWeek(dateMillis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    fun getStartOfWeek(dateMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getEndOfWeek(dateMillis: Long): Long {
        return getStartOfWeek(dateMillis) + 6 * MILLIS_PER_DAY
    }

    fun getWeekRange(semesterStartMillis: Long, weekNumber: Int): Pair<Long, Long> {
        val start = semesterStartMillis + (weekNumber - 1).toLong() * 7 * MILLIS_PER_DAY
        val end = start + 6 * MILLIS_PER_DAY
        return start to end
    }

    fun isToday(dateMillis: Long): Boolean {
        val today = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = dateMillis }
        return today.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    fun isScheduleActive(startWeek: Int, endWeek: Int, weekType: Int, currentWeek: Int): Boolean {
        if (currentWeek < startWeek || currentWeek > endWeek) return false
        return when (weekType) {
            0 -> true
            1 -> currentWeek % 2 == 1
            2 -> currentWeek % 2 == 0
            else -> true
        }
    }

    fun getPeriodTimeRange(startPeriod: Int, endPeriod: Int): String {
        val times = listOf(
            "08:00" to "08:45", "08:55" to "09:40", "10:00" to "10:45",
            "10:55" to "11:40", "14:00" to "14:45", "14:55" to "15:40",
            "16:00" to "16:45", "16:55" to "17:40", "19:00" to "19:45",
            "19:55" to "20:40", "20:50" to "21:35", "21:45" to "22:30"
        )
        val start = times.getOrNull(startPeriod - 1)?.first ?: "??:??"
        val end = times.getOrNull(endPeriod - 1)?.second ?: "??:??"
        return "$start-$end"
    }
}