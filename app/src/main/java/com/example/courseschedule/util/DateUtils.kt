package com.example.courseschedule.util

import com.example.courseschedule.data.db.entity.Semester
import java.util.*

object DateUtils {
    private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

    fun getWeekNumber(dateMillis: Long, semesterStartMillis: Long): Int {
        val startMonday = toMonday(semesterStartMillis)
        val dateMonday = toMonday(dateMillis)
        val daysDiff = (dateMonday - startMonday) / MILLIS_PER_DAY
        return (daysDiff / 7).toInt() + 1
    }

    private fun toMonday(dateMillis: Long): Long {
        val cal = Calendar.getInstance(Locale.US).apply {
            timeInMillis = dateMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getDayOfWeek(dateMillis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    fun getStartOfWeek(dateMillis: Long): Long {
        val cal = Calendar.getInstance(Locale.US).apply {
            timeInMillis = dateMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getEndOfWeek(dateMillis: Long): Long {
        return getStartOfWeek(dateMillis) + 6 * MILLIS_PER_DAY
    }

    fun getWeekRange(semesterStartMillis: Long, weekNumber: Int): Pair<Long, Long> {
        val startMonday = toMonday(semesterStartMillis)
        val start = startMonday + (weekNumber - 1).toLong() * 7 * MILLIS_PER_DAY
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

    fun getPeriodTimeRange(startPeriod: Int, endPeriod: Int, semester: Semester?): String {
        if (semester == null) return getPeriodTimeRangeStatic(startPeriod, endPeriod)
        val times = semester.getPeriodTimes()
        val start = times.getOrNull(startPeriod - 1)?.start ?: "??:??"
        val end = times.getOrNull(endPeriod - 1)?.end ?: "??:??"
        return "$start-$end"
    }

    fun getPeriodTimeRangeStatic(startPeriod: Int, endPeriod: Int): String {
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

    fun getCurrentPeriod(semester: Semester?): Int {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val minute = Calendar.getInstance().get(Calendar.MINUTE)
        val times = semester?.getPeriodTimes() ?: Semester.defaultPeriodTimes()
        for (i in times.indices) {
            val (sh, sm) = times[i].start.split(":").map { it.toIntOrNull() ?: 0 }
            val (eh, em) = times[i].end.split(":").map { it.toIntOrNull() ?: 0 }
            val startMin = sh * 60 + sm
            val endMin = eh * 60 + em
            val nowMin = hour * 60 + minute
            if (nowMin in startMin..endMin) return i + 1
        }
        if (times.isNotEmpty()) {
            val (lastH, lastM) = times.last().end.split(":").map { it.toIntOrNull() ?: 0 }
            if (hour * 60 + minute > lastH * 60 + lastM) return times.size + 1
        }
        return 0
    }
}
