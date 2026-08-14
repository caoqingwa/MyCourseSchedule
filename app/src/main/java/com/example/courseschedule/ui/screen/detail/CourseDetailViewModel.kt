package com.example.courseschedule.ui.screen.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.data.repository.CourseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    private val repository: CourseRepository
) : ViewModel() {

    @Immutable
    data class DetailUiState(
        val course: Course? = null,
        val schedules: List<Schedule> = emptyList(),
        val roomName: String? = null,
        val semester: Semester? = null
    )

    private val _courseId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<DetailUiState> = _courseId.flatMapLatest { id ->
        if (id == null) flowOf(DetailUiState())
        else flow {
            val course = repository.getCourseById(id) ?: return@flow
            val schedules = repository.getSchedulesByCourse(id)
            val roomName = course.roomId?.let { repository.getRoomById(it)?.name }
            val semester = repository.getSemesterById(course.semesterId)
            emit(DetailUiState(course, schedules, roomName, semester))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailUiState())

    fun show(courseId: Long) {
        _courseId.value = courseId
    }

    fun clear() {
        _courseId.value = null
    }
}
