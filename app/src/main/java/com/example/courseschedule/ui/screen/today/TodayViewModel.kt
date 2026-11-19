package com.example.courseschedule.ui.screen.today

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Exam
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.ui.component.CourseWithSchedule
import com.example.courseschedule.util.DateUtils
import com.example.courseschedule.util.scheduleOccurrences
import java.time.Instant
import java.time.ZoneId
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
    data class TodayExam(val exam: Exam, val name: String)

    @Immutable
    data class TodayUiState(
        val semester: Semester? = null,
        // 由 ticker 驱动重算，跨零点后会跟着变，不会像以前那样把昨天的日期一直显示着
        val todayLabel: String = "",
        val upcomingCourses: List<CourseWithSchedule> = emptyList(),
        val completedCourses: List<CourseWithSchedule> = emptyList(),
        val exams: List<TodayExam> = emptyList(),
        val hasSchedule: Boolean = false,
        val currentCourses: List<CourseWithSchedule> = emptyList(),
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
            repository.getAllRooms()
        ) { schedules, courses, rooms ->
            val courseMap = courses.associateBy { it.id }
            val roomMap = rooms.associateBy({ it.id }, { it.name })
            Triple(schedules, courseMap, roomMap)
        }.combine(repository.getExamsBySemester(semester.id)) { scheduleData, exams ->
            scheduleData to exams
        }.combine(repository.getScheduleChanges(semester.id)) { (scheduleData, exams), changes ->
            Triple(scheduleData, exams, changes)
        }.combine(ticker) { (scheduleData, exams, changes), _ ->
            val (schedules, courseMap, roomMap) = scheduleData
            val now = System.currentTimeMillis()
            val today = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = today.timeInMillis
            today.add(Calendar.DAY_OF_MONTH, 1)
            val startOfTomorrow = today.timeInMillis
            val currentWeek = DateUtils.getWeekNumber(now, semester.startDate)

            // 先判断当前日期是否处于学期时间范围内：未开学或已结课则不显示任何课程
            val beforeStart = now < DateUtils.getStartOfWeek(semester.startDate)
            val afterEnd = currentWeek > semester.totalWeeks

            val zone = ZoneId.systemDefault()
            val activeSchedules = scheduleOccurrences(
                Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
                Instant.ofEpochMilli(semester.startDate).atZone(zone).toLocalDate(),
                semester.totalWeeks, schedules, changes
            )

            val periodTimes = semester.getPeriodTimes()
            val currentPeriod = DateUtils.getCurrentPeriod(semester)

            val current = mutableListOf<CourseWithSchedule>()
            val upcoming = mutableListOf<CourseWithSchedule>()
            val completed = mutableListOf<CourseWithSchedule>()
            activeSchedules.forEach { occurrence ->
                val sched = occurrence.schedule
                val course = courseMap[sched.courseId] ?: Course(
                    id = sched.courseId, semesterId = semester.id,
                    name = "\u8bfe\u7a0b" + sched.courseId, teacher = "", color = "#CBE8BE"
                )
                val roomName = if (occurrence.change != null) occurrence.change.roomName
                    else sched.roomId?.let { roomMap[it] }
                val key = "${sched.id}:${occurrence.originalDay}:${occurrence.change != null}"
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
                val cws = CourseWithSchedule(course, sched, roomName, key, occurrence.originalDay, startTime)
                when {
                    now < startTime -> upcoming.add(cws)
                    now < endTime -> current.add(cws)
                    else -> completed.add(cws)
                }
            }

            val maxPeriod = schedules.maxOfOrNull { it.endPeriod } ?: 0
            val hasWeekend = schedules.any { it.dayOfWeek > 5 }

            TodayUiState(
                semester = semester,
                todayLabel = nowDateLabel(now),
                upcomingCourses = upcoming.sortedBy { it.startsAtMillis }, currentCourses = current,
                completedCourses = completed,
                exams = exams.filter { it.examDate in startOfDay until startOfTomorrow }
                    .map { TodayExam(it, it.notes ?: courseMap[it.courseId]?.name ?: "考试") },
                hasSchedule = schedules.isNotEmpty(),
                currentPeriod = currentPeriod,
                totalRemaining = upcoming.size + current.size,
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

    private fun nowDateLabel(millis: Long): String =
        java.text.SimpleDateFormat("yyyy\u5e74M\u6708d\u65e5 \u00b7 EEEE", Locale.CHINESE).format(Date(millis))

    fun deletePreset(semester: Semester) {
        viewModelScope.launch {
            repository.deleteSemester(semester)
        }
    }
}
