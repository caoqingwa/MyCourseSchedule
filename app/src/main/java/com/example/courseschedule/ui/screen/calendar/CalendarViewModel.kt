package com.example.courseschedule.ui.screen.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Exam
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
        val courses: List<Course> = emptyList(),
        val weeklyCourseCount: List<Int> = listOf(0,0,0,0,0)
    )

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var currentSemesterId: Long = 0

    init {
        viewModelScope.launch {
            repository.deleteExpiredExams()
            repository.getCurrentSemester().collect { semester ->
                if (semester == null) return@collect
                currentSemesterId = semester.id
                launch {
                    repository.getExamsBySemester(semester.id).collect { exams ->
                        _uiState.update { it.copy(exams = exams) }
                    }
                }
                launch {
                    repository.getCoursesBySemester(semester.id).collect { courses ->
                        _uiState.update { it.copy(courses = courses) }
                    }
                }
                val now = System.currentTimeMillis()
                val week = DateUtils.getWeekNumber(now, semester.startDate)
                val dow = DateUtils.getDayOfWeek(now)
                val schedules = repository.getSchedulesBySemester(semester.id).first()
                val counts = (1..5).map { day ->
                    schedules.count {
                        it.dayOfWeek == day && DateUtils.isScheduleActive(it.startWeek, it.endWeek, it.weekType, week)
                    }
                }
                _uiState.update {
                    it.copy(
                        semester = semester,
                        currentWeek = week,
                        currentDayOfWeek = dow,
                        todayMillis = now,
                        weeklyCourseCount = counts
                    )
                }
            }
        }
    }

    suspend fun addExam(courseId: Long, examDate: Long, reminderHours: Int, notes: String?): Long {
        return repository.insertExam(
            Exam(
                courseId = courseId,
                examDate = examDate,
                reminderHours = reminderHours,
                notes = notes
            )
        )
    }

    fun updateExam(exam: Exam) {
        viewModelScope.launch {
            repository.updateExam(exam)
        }
    }

    fun deleteExam(exam: Exam) {
        viewModelScope.launch {
            repository.deleteExam(exam)
        }
    }
}