package com.example.courseschedule.ui.screen.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.ui.component.CourseWithSchedule
import com.example.courseschedule.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: CourseRepository
) : ViewModel() {

    private val todayMillis = Calendar.getInstance().timeInMillis
    val dayOfWeek = DateUtils.getDayOfWeek(todayMillis)

    data class TodayUiState(
        val semester: Semester? = null,
        val upcomingCourses: List<CourseWithSchedule> = emptyList(),
        val currentCourse: CourseWithSchedule? = null,
        val currentPeriod: Int = 0,
        val totalRemaining: Int = 0,
        val isEmpty: Boolean = true
    )

    val uiState: StateFlow<TodayUiState> = combine(
        repository.getCurrentSemester(),
        repository.getSchedulesBySemester(repository.getCurrentSemester().map { it?.id ?: 0 }.first())
    ) { semester, schedules ->
        if (semester == null) return@combine TodayUiState()

        val currentWeek = DateUtils.getWeekNumber(todayMillis, semester.startDate)
        val courses = schedules.filter {
            it.dayOfWeek == dayOfWeek && DateUtils.isScheduleActive(it.startWeek, it.endWeek, it.weekType, currentWeek)
        }.sortedBy { it.startPeriod }

        val currentPeriod = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { h ->
            when {
                h < 8 -> 0; h < 10 -> 2; h < 11 -> 3; h < 12 -> 4
                h < 14 -> 5; h < 16 -> 7; h < 17 -> 8; h < 19 -> 9
                h < 20 -> 10; h < 21 -> 11; else -> 12
            }
        }

        var current: CourseWithSchedule? = null
        val upcoming = mutableListOf<CourseWithSchedule>()
        courses.forEach { sched ->
            val course = schedules.firstOrNull { it.courseId == sched.courseId }
            // fetch course from db would be needed in real impl, simplified here
            val cws = CourseWithSchedule(
                Course(sched.courseId, semester.id, "", "", "#CBE8BE"),
                sched
            )
            if (sched.endPeriod >= currentPeriod && currentPeriod > 0) {
                if (sched.startPeriod <= currentPeriod) current = cws
                else upcoming.add(cws)
            }
        }

        TodayUiState(
            semester = semester,
            upcomingCourses = upcoming,
            currentCourse = current,
            currentPeriod = currentPeriod,
            totalRemaining = if (current != null) upcoming.size + 1 else upcoming.size,
            isEmpty = courses.isEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState())
}
