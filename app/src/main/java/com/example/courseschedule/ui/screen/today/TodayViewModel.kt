package com.example.courseschedule.ui.screen.today

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.ui.component.CourseWithSchedule
import com.example.courseschedule.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: CourseRepository
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }

    @Immutable
    data class TodayUiState(
        val semester: Semester? = null,
        val upcomingCourses: List<CourseWithSchedule> = emptyList(),
        val currentCourse: CourseWithSchedule? = null,
        val currentPeriod: Int = 0,
        val totalRemaining: Int = 0,
        val isEmpty: Boolean = true,
        val beforeSemesterStart: Boolean = false,
        val afterSemesterEnd: Boolean = false,
        val presets: List<Semester> = emptyList(),
        val maxScheduledPeriod: Int = 0,
        val hasWeekendCourses: Boolean = false
    )

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
            repository.getAllRooms(),
            ticker
        ) { schedules, courses, rooms, _ ->
            val courseMap = courses.associateBy { it.id }
            val roomMap = rooms.associateBy({ it.id }, { it.name })
            Triple(schedules, courseMap, roomMap)
        }.map { (schedules, courseMap, roomMap) ->
            val now = System.currentTimeMillis()
            val dayOfWeek = DateUtils.getDayOfWeek(now)
            val currentWeek = DateUtils.getWeekNumber(now, semester.startDate)

            // 先判断当前日期是否处于学期时间范围内：未开学或已结课则不显示任何课程
            val beforeStart = now < DateUtils.getStartOfWeek(semester.startDate)
            val afterEnd = currentWeek > semester.totalWeeks

            val activeSchedules = if (beforeStart || afterEnd) {
                emptyList()
            } else {
                schedules.filter {
                    it.dayOfWeek == dayOfWeek && DateUtils.isScheduleActive(it.startWeek, it.endWeek, it.weekType, currentWeek)
                }.sortedBy { it.startPeriod }
            }

            val periodTimes = semester.getPeriodTimes()
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
                val endRange = periodTimes.getOrNull(sched.endPeriod - 1)
                val startRange = periodTimes.getOrNull(sched.startPeriod - 1)
                val endTime = if (endRange != null) {
                    val (h, m) = endRange.end.split(":").map { it.toIntOrNull() ?: 0 }
                    Calendar.getInstance().apply {
                        timeInMillis = now; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                } else 0L
                val startTime = if (startRange != null) {
                    val (h, m) = startRange.start.split(":").map { it.toIntOrNull() ?: 0 }
                    Calendar.getInstance().apply {
                        timeInMillis = now; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                } else Long.MAX_VALUE
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
                beforeSemesterStart = beforeStart,
                afterSemesterEnd = afterEnd,
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