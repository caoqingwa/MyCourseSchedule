package com.example.courseschedule.ui.screen.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.db.entity.Exam
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CourseRepository
) : ViewModel() {

    data class CalendarUiState(
        val semester: Semester? = null,
        val currentWeek: Int = 1,
        val currentDayOfWeek: Int = 1,
        val todayMillis: Long = System.currentTimeMillis(),
        val exams: List<Exam> = emptyList(),
        val weeklyCourseCount: List<Int> = listOf(0,0,0,0,0)
    )

    val uiState: StateFlow<CalendarUiState> = repository.getCurrentSemester().mapLatest { semester ->
        if (semester == null) return@mapLatest CalendarUiState()

        val now = System.currentTimeMillis()
        val week = DateUtils.getWeekNumber(now, semester.startDate)
        val dow = DateUtils.getDayOfWeek(now)
        val schedules = repository.getSchedulesBySemester(semester.id).first()
        val exams = repository.getExamsBySemester(semester.id).first()

        val counts = (1..5).map { day -> schedules.count { it.dayOfWeek == day && DateUtils.isScheduleActive(it.startWeek, it.endWeek, it.weekType, week) } }

        CalendarUiState(
            semester = semester,
            currentWeek = week,
            currentDayOfWeek = dow,
            todayMillis = now,
            exams = exams,
            weeklyCourseCount = counts
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())
}
