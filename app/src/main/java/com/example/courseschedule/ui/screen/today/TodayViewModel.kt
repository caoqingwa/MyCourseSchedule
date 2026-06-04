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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: CourseRepository,
    @ApplicationContext val context: Context
) : ViewModel() {

    private val todayMillis = System.currentTimeMillis()
    val dayOfWeek = DateUtils.getDayOfWeek(todayMillis)

    data class TodayUiState(
        val semester: Semester? = null,
        val upcomingCourses: List<CourseWithSchedule> = emptyList(),
        val currentCourse: CourseWithSchedule? = null,
        val currentPeriod: Int = 0,
        val totalRemaining: Int = 0,
        val isEmpty: Boolean = true,
        val presets: List<Semester> = emptyList()
    )

    val uiState: StateFlow<TodayUiState> = repository.getCurrentSemester().flatMapLatest { semester ->
        repository.getAllSemesters().flatMapLatest { presets ->
            if (semester == null) {
                flowOf(TodayUiState(presets = presets))
            } else {
                repository.getSchedulesBySemester(semester.id).map { schedules ->
                    val currentWeek = DateUtils.getWeekNumber(todayMillis, semester.startDate)
                    val activeSchedules = schedules.filter {
                        it.dayOfWeek == dayOfWeek && DateUtils.isScheduleActive(it.startWeek, it.endWeek, it.weekType, currentWeek)
                    }.sortedBy { it.startPeriod }

                    val currentPeriod = DateUtils.getCurrentPeriod(semester)

                    var current: CourseWithSchedule? = null
                    val upcoming = mutableListOf<CourseWithSchedule>()
                    activeSchedules.forEach { sched ->
                        val course = repository.getCourseById(sched.courseId) ?: Course(
                            id = sched.courseId, semesterId = semester.id,
                            name = "\u8bfe\u7a0b" + sched.courseId, teacher = "", color = "#CBE8BE"
                        )
                        val roomName = course.roomId?.let { repository.getRoomById(it)?.name }
                        val cws = CourseWithSchedule(course, sched, roomName)
                        if (sched.startPeriod <= currentPeriod && sched.endPeriod >= currentPeriod) current = cws
                        else if (sched.startPeriod > currentPeriod) upcoming.add(cws)
                    }

                    TodayUiState(
                        semester = semester, upcomingCourses = upcoming, currentCourse = current,
                        currentPeriod = currentPeriod,
                        totalRemaining = if (current != null) upcoming.size + 1 else upcoming.size,
                        isEmpty = activeSchedules.isEmpty(),
                        presets = presets
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState())

    fun saveSemester(name: String, startDateMillis: Long, totalWeeks: Int, periodCount: Int, periodTimesJson: String) {
        viewModelScope.launch {
            val current = repository.getCurrentSemester().first()
            if (current != null) {
                repository.updateSemester(current.copy(
                    name = name, startDate = startDateMillis, totalWeeks = totalWeeks,
                    periodCount = periodCount, periodTimesJson = periodTimesJson
                ))
            } else {
                repository.insertSemester(Semester(
                    name = name, startDate = startDateMillis, totalWeeks = totalWeeks,
                    periodCount = periodCount, periodTimesJson = periodTimesJson
                ))
            }
        }
    }

    fun deletePreset(semester: Semester) {
        viewModelScope.launch {
            repository.deleteSemester(semester)
        }
    }
}
