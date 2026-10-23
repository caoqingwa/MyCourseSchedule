package com.example.courseschedule.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekSelectionTest {

    @Test
    fun contiguousWeeksBecomeOneSpan() {
        assertEquals(listOf(WeekSpan(1, 16)), groupWeeksForSchedules((1..16).toSet()))
    }

    @Test
    fun fullOddSelectionBecomesOddSpan() {
        assertEquals(listOf(WeekSpan(1, 15, 1)), groupWeeksForSchedules((1..15 step 2).toSet()))
    }

    @Test
    fun fullEvenSelectionBecomesEvenSpan() {
        assertEquals(listOf(WeekSpan(2, 16, 2)), groupWeeksForSchedules((2..16 step 2).toSet()))
    }

    @Test
    fun gapsSplitIntoContiguousSpans() {
        assertEquals(
            listOf(WeekSpan(1, 2), WeekSpan(5, 6)),
            groupWeeksForSchedules(setOf(1, 2, 5, 6))
        )
    }

    @Test
    fun singleWeekStaysSingleSpan() {
        assertEquals(listOf(WeekSpan(7, 7)), groupWeeksForSchedules(setOf(7)))
    }

    @Test
    fun spanExpandsBackToSelectedWeeks() {
        assertEquals((1..16).toSet(), weeksForSpan(1, 16, 0))
        assertEquals((1..15 step 2).toSet(), weeksForSpan(1, 15, 1))
        assertEquals((2..16 step 2).toSet(), weeksForSpan(2, 16, 2))
        assertTrue(weeksForSpan(5, 4, 0).isEmpty())
    }

    @Test
    fun patternToggleAddsAndRemoves() {
        val all = (1..10).toSet()
        val odd = (1..10 step 2).toSet()
        assertEquals(odd, toggleWeekPattern(emptySet(), odd))
        assertTrue(toggleWeekPattern(odd, odd).isEmpty())
        assertEquals((2..10 step 2).toSet(), toggleWeekPattern(all, odd))
    }

    @Test
    fun emptySelectionProducesNoSchedule() {
        assertTrue(groupWeeksForSchedules(emptySet()).isEmpty())
    }
}
