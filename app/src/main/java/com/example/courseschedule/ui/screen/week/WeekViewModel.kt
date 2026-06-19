package com.example.courseschedule.ui.screen.week

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Room
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.ui.navigation.NavigationState
import com.example.courseschedule.ui.theme.CourseColors
import com.example.courseschedule.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekViewModel @Inject constructor(
    private val repository: CourseRepository
) : ViewModel() {

    @Immutable
    data class WeekPageData(
        val schedules: List<Schedule> = emptyList(),
        val courseMap: Map<Long, Course> = emptyMap(),
        val roomMap: Map<Long, String> = emptyMap(),
        val weekRange: String = ""
    )

    @Immutable
    data class WeekUiState(
        val semester: Semester? = null,
        val currentWeek: Int = 1,
        val selectedWeek: Int = 1,
        val currentDayOfWeek: Int = 1,
        val highlightDayOfWeek: Int = 0,
        val totalWeeks: Int = 20,
        val currentPage: WeekPageData = WeekPageData(),
        val presets: List<Semester> = emptyList(),
        val maxScheduledPeriod: Int = 0
    )

    @Immutable
    data class ConflictInfo(
        val courseName: String,
        val dayOfWeek: Int,
        val period: Int
    )

    private val _selectedWeek = MutableStateFlow(0)
    private val _highlightDayOfWeek = MutableStateFlow(0)

    init {
        consumeNavigationState()
    }

    private fun consumeNavigationState() {
        // Set highlight FIRST so combine sees it when _selectedWeek triggers
        val targetDay = NavigationState.targetDayOfWeek
        if (targetDay > 0) {
            _highlightDayOfWeek.value = targetDay
            NavigationState.targetDayOfWeek = 0
        }
        val targetWeek = NavigationState.targetWeek
        if (targetWeek > 0) {
            _selectedWeek.value = targetWeek
            NavigationState.targetWeek = 0
        }
    }

    fun consumeTargetWeek() {
        consumeNavigationState()
    }

    fun clearHighlight() {
        _highlightDayOfWeek.value = 0
    }

    fun selectWeek(week: Int, highlightDay: Int = 0) {
        if (highlightDay > 0) _highlightDayOfWeek.value = highlightDay
        _selectedWeek.value = week.coerceIn(1, uiState.value.totalWeeks.coerceAtLeast(1))
    }

    private fun buildWeekPage(
        allSchedules: List<Schedule>,
        coursesMap: Map<Long, Course>,
        roomMap: Map<Long, String>,
        semester: Semester,
        week: Int
    ): WeekPageData {
        if (week < 1 || week > semester.totalWeeks) return WeekPageData()
        val active = allSchedules.filter {
            DateUtils.isScheduleActive(it.startWeek, it.endWeek, it.weekType, week)
        }
        val courses = mutableMapOf<Long, Course>()
        active.forEach { s -> coursesMap[s.courseId]?.let { courses[s.courseId] = it } }
        val range = DateUtils.getWeekRange(semester.startDate, week)
        val rangeStr = FMT_FULL.format(Date(range.first)) + " ~ " +
            FMT_SHORT.format(Date(range.second))
        return WeekPageData(schedules = active, courseMap = courses, roomMap = roomMap, weekRange = rangeStr)
    }

    companion object {
        private val FMT_FULL = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        private val FMT_SHORT = SimpleDateFormat("MM.dd", Locale.getDefault())
    }

    val uiState: StateFlow<WeekUiState> = repository.getCurrentSemester()
        .flatMapLatest { semester ->
            if (semester == null) {
                flowOf(WeekUiState())
            } else {
                combine(
                    repository.getSchedulesBySemester(semester.id),
                    repository.getCoursesBySemester(semester.id).map { it.associateBy { c -> c.id } },
                    repository.getAllRooms().map { it.associateBy({ r -> r.id }, { r -> r.name }) },
                    repository.getAllSemesters(),
                    combine(_selectedWeek, _highlightDayOfWeek) { w, h -> w to h }
                ) { schedules, courseMap, roomMap, presets, weekAndHighlight ->
                    val (selectedWeek, highlightDay) = weekAndHighlight
                    val now = System.currentTimeMillis()
                    val currentWeek = DateUtils.getWeekNumber(now, semester.startDate)
                    val dow = DateUtils.getDayOfWeek(now)
                    val displayWeek = (if (selectedWeek > 0) selectedWeek else currentWeek)
                        .coerceIn(1, semester.totalWeeks)
                    val currentPage = buildWeekPage(schedules, courseMap, roomMap, semester, displayWeek)
                    val maxPeriod = schedules.maxOfOrNull { it.endPeriod } ?: 0
                    WeekUiState(
                        semester = semester,
                        currentWeek = currentWeek,
                        selectedWeek = displayWeek,
                        currentDayOfWeek = dow,
                        highlightDayOfWeek = highlightDay,
                        totalWeeks = semester.totalWeeks,
                        currentPage = currentPage,
                        presets = presets,
                        maxScheduledPeriod = maxPeriod
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekUiState())

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
                _selectedWeek.value = 0
            }
        }
    }

    fun deletePreset(semester: Semester) {
        viewModelScope.launch {
            repository.deleteSemester(semester)
        }
    }

    fun addCourse(
        dayOfWeek: Int, name: String, teacher: String, room: String,
        weekType: Int, startWeek: Int, endWeek: Int, startPeriod: Int, endPeriod: Int
    ) {
        viewModelScope.launch {
            val semester = repository.getCurrentSemester().first() ?: return@launch
            val colorIdx = (System.currentTimeMillis() % CourseColors.size).toInt()
            val roomId = if (room.isNotBlank()) repository.insertRoom(Room(name = room)) else null
            val course = Course(
                semesterId = semester.id, name = name, teacher = teacher,
                color = colorIdx.toString(), roomId = roomId
            )
            val schedule = Schedule(
                courseId = 0, dayOfWeek = dayOfWeek, startPeriod = startPeriod,
                endPeriod = endPeriod, startWeek = startWeek, endWeek = endWeek, weekType = weekType
            )
            repository.insertCourseWithSchedule(course, schedule)
        }
    }

    fun updateCourseAndSchedule(
        courseId: Long, scheduleId: Long,
        name: String, teacher: String, room: String,
        dayOfWeek: Int, weekType: Int, startWeek: Int, endWeek: Int, startPeriod: Int, endPeriod: Int
    ) {
        viewModelScope.launch {
            val course = repository.getCourseById(courseId) ?: return@launch
            val roomId = if (room.isNotBlank()) {
                course.roomId?.let { repository.updateRoom(it, room); it }
                    ?: repository.insertRoom(Room(name = room))
            } else course.roomId
            repository.updateCourse(course.copy(name = name, teacher = teacher, roomId = roomId))

            repository.updateSchedule(
                Schedule(id = scheduleId, courseId = courseId, dayOfWeek = dayOfWeek,
                    startPeriod = startPeriod, endPeriod = endPeriod,
                    startWeek = startWeek, endWeek = endWeek, weekType = weekType)
            )
        }
    }

    fun deleteCourse(courseId: Long) {
        viewModelScope.launch {
            repository.deleteCourseWithSchedules(courseId)
        }
    }

    suspend fun checkConflict(
        dayOfWeek: Int, startPeriod: Int, endPeriod: Int,
        weekType: Int, startWeek: Int, endWeek: Int,
        excludeScheduleId: Long = 0
    ): List<ConflictInfo> {
        val semester = repository.getCurrentSemester().first() ?: return emptyList()
        val schedules = repository.getSchedulesBySemester(semester.id).first()
        val courseMap = repository.getCoursesBySemester(semester.id).first().associateBy { it.id }
        val conflicts = mutableListOf<ConflictInfo>()
        for (existing in schedules) {
            if (existing.id == excludeScheduleId) continue
            if (existing.dayOfWeek != dayOfWeek) continue
            val periodOverlap = startPeriod <= existing.endPeriod && endPeriod >= existing.startPeriod
            if (!periodOverlap) continue
            val weekOverlap = startWeek <= existing.endWeek && endWeek >= existing.startWeek
            if (!weekOverlap) continue
            val typeConflict = weekType == 0 || existing.weekType == 0 || weekType == existing.weekType
            if (!typeConflict) continue
            val name = courseMap[existing.courseId]?.name ?: "\u8bfe\u7a0b"
            conflicts.add(ConflictInfo(name, dayOfWeek, existing.startPeriod))
        }
        return conflicts
    }
}