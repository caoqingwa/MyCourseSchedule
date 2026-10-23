package com.example.courseschedule.util

/** One schedulable week span; weekType matches Schedule.weekType (0 all, 1 odd, 2 even). */
data class WeekSpan(
    val startWeek: Int,
    val endWeek: Int,
    val weekType: Int = 0
)

/** Expands a stored schedule span back into concrete week numbers. */
fun weeksForSpan(startWeek: Int, endWeek: Int, weekType: Int): Set<Int> {
    if (endWeek < startWeek) return emptySet()
    return (startWeek..endWeek).filter { week ->
        when (weekType) {
            1 -> week % 2 == 1
            2 -> week % 2 == 0
            else -> true
        }
    }.toSet()
}

/** Toggles a preset week pattern: add it when incomplete, remove it when fully selected. */
fun toggleWeekPattern(current: Set<Int>, pattern: Set<Int>): Set<Int> =
    if (current.containsAll(pattern)) current - pattern else current + pattern

/**
 * Compresses arbitrary selected weeks into Schedule-compatible spans.
 * A full odd/even sequence becomes one span; other gaps become contiguous runs.
 */
fun groupWeeksForSchedules(weeks: Set<Int>): List<WeekSpan> {
    val sorted = weeks.filter { it > 0 }.sorted()
    if (sorted.isEmpty()) return emptyList()

    val first = sorted.first()
    val last = sorted.last()
    if (first < last) {
        val parity = first % 2
        val expectedSize = (last - first) / 2 + 1
        val isFullParity = sorted.size == expectedSize && sorted.all { it % 2 == parity }
        if (isFullParity) {
            return listOf(WeekSpan(first, last, if (parity == 1) 1 else 2))
        }
    }

    val spans = mutableListOf<WeekSpan>()
    var start = first
    var previous = first
    for (week in sorted.drop(1)) {
        if (week != previous + 1) {
            spans += WeekSpan(start, previous)
            start = week
        }
        previous = week
    }
    spans += WeekSpan(start, previous)
    return spans
}
