package com.example.courseschedule.ui.screen.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

@HiltViewModel
class WeekViewModel @Inject constructor(
    private val repository: CourseRepository
) : ViewModel() {

    data class WeekUiState(
        val currentWeek: Int = 1,
        val currentDayOfWeek: Int = 1,
        val weekRange: String = "",
        val schedules: List<Schedule> = emptyList(),
        val courseMap: Map<Long, Course> = emptyMap()
    )

    val uiState: StateFlow<WeekUiState> = repository.getCurrentSemester().mapLatest { semester ->
        if (semester == null) return@mapLatest WeekUiState()

        val now = System.currentTimeMillis()
        val week = DateUtils.getWeekNumber(now, semester.startDate)
        val dow = DateUtils.getDayOfWeek(now)
        val range = DateUtils.getWeekRange(semester.startDate, week)
        val rangeStr = java.text.SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(range.first)) + " ~ " + java.text.SimpleDateFormat("MM.dd", Locale.getDefault()).format(Date(range.second))

        val schedules = repository.getSchedulesBySemester(semester.id).first()
        val courses = mutableMapOf<Long, Course>()
        schedules.forEach { s ->
            if (!courses.containsKey(s.courseId)) {
                repository.getCourseById(s.courseId)?.let { courses[s.courseId] = it }
            }
        }

        WeekUiState(currentWeek = week, currentDayOfWeek = dow, weekRange = rangeStr, schedules = schedules, courseMap = courses)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekUiState())
}
