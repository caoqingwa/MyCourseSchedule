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
        /** 各时段教室名：scheduleId -> roomName（编辑/展示按时段对应） */
        val scheduleRooms: Map<Long, String> = emptyMap(),
        /** 课程教室聚合文案：所有时段教室去重拼接（多教室显示如"A101、B202"） */
        val roomSummary: String = "",
        val semester: Semester? = null
    )

    private val _courseId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<DetailUiState> = _courseId.flatMapLatest { id ->
        if (id == null) flowOf(DetailUiState())
        else flow {
            val course = repository.getCourseById(id) ?: return@flow
            val schedules = repository.getSchedulesByCourse(id)
            val scheduleRooms = schedules.mapNotNull { s ->
                s.roomId?.let { rid -> repository.getRoomById(rid)?.name?.let { s.id to it } }
            }.toMap()
            val roomSummary = scheduleRooms.values.filter { it.isNotBlank() }.distinct().joinToString("\u3001")
            val semester = repository.getSemesterById(course.semesterId)
            emit(DetailUiState(course, schedules, scheduleRooms, roomSummary, semester))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailUiState())

    fun show(courseId: Long) {
        _courseId.value = courseId
    }

    fun clear() {
        _courseId.value = null
    }
}
