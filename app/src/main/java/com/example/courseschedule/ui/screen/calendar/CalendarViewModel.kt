package com.example.courseschedule.ui.screen.calendar

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Exam
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.util.DateUtils
import com.example.courseschedule.worker.ExamReminderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CourseRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    @Immutable
    data class CalendarUiState(
        val semester: Semester? = null,
        val currentWeek: Int = 1,
        val currentDayOfWeek: Int = 1,
        val todayMillis: Long = System.currentTimeMillis(),
        val exams: List<Exam> = emptyList(),
        val courses: List<Course> = emptyList(),
        val weeklyCourseCount: List<Int> = listOf(0,0,0,0,0),
        val notificationsEnabled: Boolean = true
    )

    private val _notificationsEnabled = MutableStateFlow(true)

    val uiState: StateFlow<CalendarUiState> = combine(
        repository.getCurrentSemester(),
        _notificationsEnabled
    ) { semester, enabled -> semester to enabled }
    .flatMapLatest { (semester, enabled) ->
        if (semester == null) {
            flowOf(CalendarUiState(notificationsEnabled = enabled))
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
                        weeklyCourseCount = counts,
                        notificationsEnabled = enabled
                    )
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    init {
        viewModelScope.launch { repository.deleteExpiredExams() }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        if (enabled) {
            viewModelScope.launch {
                ExamReminderWorker.rescheduleAll(appContext, repository.getExamDao())
            }
        }
    }

    suspend fun addExam(courseId: Long, examDate: Long, reminderHours: Int, notes: String?): Long {
        val examId = repository.insertExam(
            Exam(courseId = courseId, examDate = examDate, reminderHours = reminderHours, notes = notes)
        )
        if (_notificationsEnabled.value) {
            ExamReminderWorker.schedule(appContext, notes ?: "\u8003\u8bd5", examDate, reminderHours, examId)
        }
        return examId
    }

    fun updateExam(exam: Exam) {
        viewModelScope.launch {
            repository.updateExam(exam)
            if (_notificationsEnabled.value) {
                ExamReminderWorker.schedule(appContext, exam.notes ?: "\u8003\u8bd5", exam.examDate, exam.reminderHours, exam.id)
            }
        }
    }

    fun deleteExam(exam: Exam) {
        viewModelScope.launch { repository.deleteExam(exam) }
    }
}
