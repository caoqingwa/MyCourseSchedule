package com.example.courseschedule.ui.screen.today

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.ui.component.CourseWithSchedule
import com.example.courseschedule.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: CourseRepository,
    @ApplicationContext val context: Context
) : ViewModel() {

    private val todayMillis = System.currentTimeMillis()
    val dayOfWeek = DateUtils.getDayOfWeek(todayMillis)

    @Immutable
    data class TodayUiState(
        val semester: Semester? = null,
        val upcomingCourses: List<CourseWithSchedule> = emptyList(),
        val currentCourse: CourseWithSchedule? = null,
        val currentPeriod: Int = 0,
        val totalRemaining: Int = 0,
        val isEmpty: Boolean = true,
        val presets: List<Semester> = emptyList(),
        val maxScheduledPeriod: Int = 0,
        val hasWeekendCourses: Boolean = false
    )

    private fun getPeriodEndMillis(period: Int, semester: Semester): Long {
        val times = semester.getPeriodTimes()
        val range = times.getOrNull(period - 1) ?: return 0L
        val (h, m) = range.end.split(":").map { it.toIntOrNull() ?: 0 }
        val cal = Calendar.getInstance().apply {
            timeInMillis = todayMillis
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun getPeriodStartMillis(period: Int, semester: Semester): Long {
        val times = semester.getPeriodTimes()
        val range = times.getOrNull(period - 1) ?: return Long.MAX_VALUE
        val (h, m) = range.start.split(":").map { it.toIntOrNull() ?: 0 }
        val cal = Calendar.getInstance().apply {
            timeInMillis = todayMillis
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    val uiState: StateFlow<TodayUiState> = combine(
        repository.getCurrentSemester(),
        repository.getAllSemesters()
    ) { semester, presets -> semester to presets }
    .flatMapLatest { (semester, presets) ->
        if (semester == null) {
            return@flatMapLatest flowOf(TodayUiState(presets = presets))
        }
        combine(
            repository.getSchedulesBySemester(semester.id),
            repository.getCoursesBySemester(semester.id),
            repository.getAllRooms()
        ) { schedules, courses, rooms ->
            val courseMap = courses.associateBy { it.id }
            val roomMap = rooms.associateBy({ it.id }, { it.name })
            Triple(schedules, courseMap, roomMap)
        }.map { (schedules, courseMap, roomMap) ->
            val currentWeek = DateUtils.getWeekNumber(todayMillis, semester.startDate)
            val activeSchedules = schedules.filter {
                it.dayOfWeek == dayOfWeek && DateUtils.isScheduleActive(it.startWeek, it.endWeek, it.weekType, currentWeek)
            }.sortedBy { it.startPeriod }

            val now = System.currentTimeMillis()
            val currentPeriod = DateUtils.getCurrentPeriod(semester)

            var current: CourseWithSchedule? = null
            val upcoming = mutableListOf<CourseWithSchedule>()
            activeSchedules.forEach { sched ->
                val course = courseMap[sched.courseId] ?: Course(
                    id = sched.courseId, semesterId = semester.id,
                    name = "\u8bfe\u7a0b" + sched.courseId, teacher = "", color = "#CBE8BE"
                )
                val roomName = course.roomId?.let { roomMap[it] }
                val cws = CourseWithSchedule(course, sched, roomName)
                val endTime = getPeriodEndMillis(sched.endPeriod, semester)
                val startTime = getPeriodStartMillis(sched.startPeriod, semester)
                when {
                    now < startTime -> upcoming.add(cws)
                    now >= startTime && now < endTime -> current = cws
                    else -> { /* course already finished, skip */ }
                }
            }

            val maxPeriod = schedules.maxOfOrNull { it.endPeriod } ?: 0
            val hasWeekend = schedules.any { it.dayOfWeek > 5 }

            TodayUiState(
                semester = semester, upcomingCourses = upcoming, currentCourse = current,
                currentPeriod = currentPeriod,
                totalRemaining = if (current != null) upcoming.size + 1 else upcoming.size,
                isEmpty = activeSchedules.isEmpty(),
                presets = presets,
                maxScheduledPeriod = maxPeriod,
                hasWeekendCourses = hasWeekend
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState())

    fun saveSemester(name: String, startDateMillis: Long, totalWeeks: Int, periodCount: Int, weekDays: Int, periodTimesJson: String) {
        viewModelScope.launch {
            repository.saveSemesterCurrent(name, startDateMillis, totalWeeks, periodCount, weekDays, periodTimesJson)
        }
    }

    fun deletePreset(semester: Semester) {
        viewModelScope.launch {
            repository.deleteSemester(semester)
        }
    }
}