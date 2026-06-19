package com.example.courseschedule.ui.screen.calendar

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Exam
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CourseRepository
) : ViewModel() {

    @Immutable
    data class CalendarUiState(
        val semester: Semester? = null,
        val currentWeek: Int = 1,
        val currentDayOfWeek: Int = 1,
        val todayMillis: Long = System.currentTimeMillis(),
        val exams: List<Exam> = emptyList(),
        val courses: List<Course> = emptyList(),
        val weeklyCourseCount: List<Int> = listOf(0,0,0,0,0)
    )

    val uiState: StateFlow<CalendarUiState> = repository.getCurrentSemester()
        .flatMapLatest { semester ->
            if (semester == null) {
                flowOf(CalendarUiState())
            } else {
                combine(
                    repository.getExamsBySemester(semester.id),
                    repository.getCoursesBySemester(semester.id)
                ) { exams, courses -> exams to courses }
                    .map { (exams, courses) ->
                        val now = System.currentTimeMillis()
                        val week = DateUtils.getWeekNumber(now, semester.startDate)
                        val dow = DateUtils.getDayOfWeek(now)
                        val schedules = repository.getSchedulesBySemester(semester.id).first()
                        val counts = (1..5).map { day ->
                            schedules.count {
                                it.dayOfWeek == day && DateUtils.isScheduleActive(it.startWeek, it.endWeek, it.weekType, week)
                            }
                        }
                        CalendarUiState(
                            semester = semester,
                            currentWeek = week,
                            currentDayOfWeek = dow,
                            todayMillis = now,
                            exams = exams,
                            courses = courses,
                            weeklyCourseCount = counts
                        )
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    init {
        viewModelScope.launch { repository.deleteExpiredExams() }
    }

    suspend fun addExam(courseId: Long, examDate: Long, reminderHours: Int, notes: String?): Long {
        return repository.insertExam(
            Exam(courseId = courseId, examDate = examDate, reminderHours = reminderHours, notes = notes)
        )
    }

    fun updateExam(exam: Exam) {
        viewModelScope.launch { repository.updateExam(exam) }
    }

    fun deleteExam(exam: Exam) {
        viewModelScope.launch { repository.deleteExam(exam) }
    }
}