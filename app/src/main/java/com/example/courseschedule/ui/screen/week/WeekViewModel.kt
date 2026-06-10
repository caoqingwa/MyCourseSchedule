package com.example.courseschedule.ui.screen.week

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekViewModel @Inject constructor(
    private val repository: CourseRepository
) : ViewModel() {

    data class WeekPageData(
        val schedules: List<Schedule> = emptyList(),
        val courseMap: Map<Long, Course> = emptyMap(),
        val roomMap: Map<Long, String> = emptyMap(),
        val weekRange: String = ""
    )

    data class WeekUiState(
        val semester: Semester? = null,
        val currentWeek: Int = 1,
        val selectedWeek: Int = 1,
        val currentDayOfWeek: Int = 1,
        val totalWeeks: Int = 20,
        val currentPage: WeekPageData = WeekPageData(),
        val presets: List<Semester> = emptyList()
    )

    private data class WeekLoadInput(
        val semester: Semester,
        val schedules: List<Schedule>,
        val courses: List<Course>,
        val rooms: List<Room>,
        val presets: List<Semester>,
        val selectedWeek: Int
    )

    private val _selectedWeek = MutableStateFlow(0)

    init {
        val targetWeek = NavigationState.targetWeek
        if (targetWeek > 0) {
            _selectedWeek.value = targetWeek
            NavigationState.targetWeek = 0
        }
    }

    fun consumeTargetWeek() {
        val tw = NavigationState.targetWeek
        if (tw > 0) {
            _selectedWeek.value = tw
            NavigationState.targetWeek = 0
        }
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
        val fmt = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        val rangeStr = fmt.format(Date(range.first)) + " ~ " +
            SimpleDateFormat("MM.dd", Locale.getDefault()).format(Date(range.second))
        return WeekPageData(schedules = active, courseMap = courses, roomMap = roomMap, weekRange = rangeStr)
    }

    val uiState: StateFlow<WeekUiState> = repository.getCurrentSemester()
        .flatMapLatest { semester ->
            if (semester == null) {
                flowOf(WeekUiState())
            } else {
                combine(
                    repository.getSchedulesBySemester(semester.id),
                    repository.getCoursesBySemester(semester.id),
                    repository.getAllRooms(),
                    repository.getAllSemesters(),
                    _selectedWeek
                ) { schedules, courses, rooms, presets, selectedWeek ->
                    WeekLoadInput(
                        semester = semester,
                        schedules = schedules,
                        courses = courses,
                        rooms = rooms,
                        presets = presets,
                        selectedWeek = selectedWeek
                    )
                }.mapLatest { input ->
                    withContext(Dispatchers.Default) {
                        val now = System.currentTimeMillis()
                        val currentWeek = DateUtils.getWeekNumber(now, input.semester.startDate)
                        val dow = DateUtils.getDayOfWeek(now)
                        val displayWeek = (if (input.selectedWeek > 0) input.selectedWeek else currentWeek)
                            .coerceIn(1, input.semester.totalWeeks)

                        val currentPage = buildWeekPage(
                            input.schedules,
                            input.courses.associateBy { it.id },
                            input.rooms.associateBy({ it.id }, { it.name }),
                            input.semester,
                            displayWeek
                        )

                        WeekUiState(
                            semester = input.semester,
                            currentWeek = currentWeek,
                            selectedWeek = displayWeek,
                            currentDayOfWeek = dow,
                            totalWeeks = input.semester.totalWeeks,
                            currentPage = currentPage,
                            presets = input.presets
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekUiState())

    fun selectWeek(week: Int) {
        _selectedWeek.value = week.coerceIn(1, uiState.value.totalWeeks.coerceAtLeast(1))
    }

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
            val courseId = repository.insertCourse(
                Course(semesterId = semester.id, name = name, teacher = teacher, color = colorIdx.toString(), roomId = roomId)
            )
            repository.insertSchedule(
                Schedule(courseId = courseId, dayOfWeek = dayOfWeek, startPeriod = startPeriod,
                    endPeriod = endPeriod, startWeek = startWeek, endWeek = endWeek, weekType = weekType)
            )
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
            repository.deleteSchedulesByCourseId(courseId)
            val course = repository.getCourseById(courseId) ?: return@launch
            repository.deleteCourse(course)
        }
    }
}