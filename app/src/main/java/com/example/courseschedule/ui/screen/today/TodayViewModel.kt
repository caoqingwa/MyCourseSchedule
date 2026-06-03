package com.example.courseschedule.ui.screen.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.ui.component.CourseWithSchedule
import com.example.courseschedule.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: CourseRepository,
    @ApplicationContext val context: Context
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

    val uiState: StateFlow<TodayUiState> = repository.getCurrentSemester().flatMapLatest { semester ->
        if (semester == null) {
            flowOf(TodayUiState())
        } else {
            repository.getSchedulesBySemester(semester.id).map { schedules ->
                val currentWeek = DateUtils.getWeekNumber(todayMillis, semester.startDate)
                val activeSchedules = schedules.filter {
                    it.dayOfWeek == dayOfWeek && DateUtils.isScheduleActive(it.startWeek, it.endWeek, it.weekType, currentWeek)
                }.sortedBy { it.startPeriod }

                val currentPeriod = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { h ->
                    when {
                        h < 8 -> 0
                        h < 9 -> 1
                        h < 10 -> 2
                        h < 11 -> 3
                        h < 12 -> 4
                        h < 14 -> 5
                        h < 15 -> 6
                        h < 16 -> 7
                        h < 17 -> 8
                        h < 19 -> 9
                        h < 20 -> 10
                        h < 21 -> 11
                        else -> 12
                    }
                }

                var current: CourseWithSchedule? = null
                val upcoming = mutableListOf<CourseWithSchedule>()
                activeSchedules.forEach { sched ->
                    val course = repository.getCourseById(sched.courseId) ?: Course(
                        id = sched.courseId,
                        semesterId = semester.id,
                        name = "\u8bfe\u7a0b" + sched.courseId,
                        teacher = "",
                        color = "#CBE8BE"
                    )
                    val roomName = course.roomId?.let { repository.getRoomById(it)?.name }
                    val cws = CourseWithSchedule(course, sched, roomName)
                    if (sched.startPeriod <= currentPeriod && sched.endPeriod >= currentPeriod) {
                        current = cws
                    } else if (sched.startPeriod > currentPeriod) {
                        upcoming.add(cws)
                    }
                }

                TodayUiState(
                    semester = semester,
                    upcomingCourses = upcoming,
                    currentCourse = current,
                    currentPeriod = currentPeriod,
                    totalRemaining = if (current != null) upcoming.size + 1 else upcoming.size,
                    isEmpty = activeSchedules.isEmpty()
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState())
}
