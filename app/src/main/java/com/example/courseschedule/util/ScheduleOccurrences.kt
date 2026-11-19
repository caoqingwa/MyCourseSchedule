package com.example.courseschedule.util

import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.db.entity.ScheduleChange
import java.time.LocalDate

data class ScheduleOccurrence(
    val schedule: Schedule,
    val originalDay: Long,
    val change: ScheduleChange? = null
)

fun scheduleOccurrences(
    date: LocalDate,
    semesterStart: LocalDate,
    totalWeeks: Int,
    schedules: List<Schedule>,
    changes: List<ScheduleChange>
): List<ScheduleOccurrence> {
    val day = date.toEpochDay()
    val offset = day - semesterStart.toEpochDay() + semesterStart.dayOfWeek.value - 1
    val suppressed = changes.mapNotNullTo(mutableSetOf()) {
        it.scheduleId.takeIf { _ -> it.originalDay == day && !it.keepOriginal }
    }
    val arrivals = changes.filter { it.targetDay == day }
    val byId = if (arrivals.isEmpty()) emptyMap() else schedules.associateBy { it.id }
    return buildList {
        if (offset in 0L until totalWeeks.toLong() * 7) {
            val week = (offset / 7).toInt() + 1
            for (schedule in schedules) {
                if (schedule.id !in suppressed && schedule.dayOfWeek == date.dayOfWeek.value &&
                    DateUtils.isScheduleActive(schedule.startWeek, schedule.endWeek, schedule.weekType, week)
                ) add(ScheduleOccurrence(schedule, day))
            }
        }
        for (change in arrivals) {
            val schedule = byId[change.scheduleId] ?: continue
            add(ScheduleOccurrence(schedule.copy(
                dayOfWeek = date.dayOfWeek.value,
                startPeriod = change.startPeriod,
                endPeriod = change.endPeriod
            ), change.originalDay, change))
        }
    }.sortedBy { it.schedule.startPeriod }
}
