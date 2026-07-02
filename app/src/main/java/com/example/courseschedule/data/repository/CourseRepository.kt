package com.example.courseschedule.data.repository

import androidx.room.Transaction
import com.example.courseschedule.data.db.dao.*
import com.example.courseschedule.data.db.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepository @Inject constructor(
    private val semesterDao: SemesterDao,
    private val courseDao: CourseDao,
    private val scheduleDao: ScheduleDao,
    private val roomDao: RoomDao,
    private val examDao: ExamDao
) {
    fun getCurrentSemester(): Flow<Semester?> = semesterDao.getCurrent()
    fun getAllSemesters(): Flow<List<Semester>> = semesterDao.getAll()
    suspend fun getSemesterById(id: Long): Semester? = semesterDao.getById(id)
    suspend fun insertSemester(semester: Semester): Long = semesterDao.insert(semester)
    suspend fun updateSemester(semester: Semester) = semesterDao.update(semester)
    suspend fun deleteSemester(semester: Semester) = semesterDao.delete(semester)

    suspend fun saveSemesterCurrent(
        name: String, startDateMillis: Long, totalWeeks: Int,
        periodCount: Int, weekDays: Int, periodTimesJson: String
    ) {
        val current = getCurrentSemester().first()
        if (current != null) {
            updateSemester(current.copy(
                name = name, startDate = startDateMillis, totalWeeks = totalWeeks,
                periodCount = periodCount, weekDays = weekDays, periodTimesJson = periodTimesJson
            ))
        } else {
            insertSemester(Semester(
                name = name, startDate = startDateMillis, totalWeeks = totalWeeks,
                periodCount = periodCount, weekDays = weekDays, periodTimesJson = periodTimesJson
            ))
        }
    }

    suspend fun initDefaultSemester(): Long? {
        val existing = semesterDao.getAll().first()
        if (existing.isNotEmpty()) return existing.first().id
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return semesterDao.insert(
            Semester(name = "2025-2026 \u7b2c\u4e00\u5b66\u671f", startDate = cal.timeInMillis, totalWeeks = 20)
        )
    }

    fun getCoursesBySemester(semesterId: Long): Flow<List<Course>> = courseDao.getBySemester(semesterId)
    suspend fun getCourseById(id: Long): Course? = courseDao.getById(id)
    suspend fun insertCourse(course: Course): Long = courseDao.insert(course)
    suspend fun updateCourse(course: Course) = courseDao.update(course)
    suspend fun deleteCourse(course: Course) = courseDao.delete(course)

    fun getSchedulesBySemester(semesterId: Long): Flow<List<Schedule>> = scheduleDao.getBySemester(semesterId)
    suspend fun insertSchedule(schedule: Schedule): Long = scheduleDao.insert(schedule)
    suspend fun updateSchedule(schedule: Schedule) = scheduleDao.update(schedule)
    suspend fun deleteSchedule(schedule: Schedule) = scheduleDao.delete(schedule)
    suspend fun deleteSchedulesByCourseId(courseId: Long) = scheduleDao.deleteByCourseId(courseId)

    suspend fun hasWeekendCourses(semesterId: Long): Boolean = scheduleDao.countWeekendSchedules(semesterId) > 0

    suspend fun insertRoom(room: Room): Long = roomDao.insert(room)
    fun getAllRooms(): Flow<List<Room>> = roomDao.getAll()
    suspend fun getRoomById(id: Long): Room? = roomDao.getById(id)
    suspend fun updateRoom(roomId: Long, newName: String) {
        roomDao.getById(roomId)?.let { r: Room -> roomDao.update(r.copy(name = newName)) }
    }

    fun getExamsBySemester(semesterId: Long): Flow<List<Exam>> = examDao.getBySemester(semesterId)
    fun getExamDao(): ExamDao = examDao
    suspend fun insertExam(exam: Exam): Long = examDao.insert(exam)
    suspend fun updateExam(exam: Exam) = examDao.update(exam)
    suspend fun deleteExam(exam: Exam) = examDao.delete(exam)
    suspend fun deleteExpiredExams() = examDao.deleteExpired(System.currentTimeMillis())

    @Transaction
    suspend fun deleteCourseWithSchedules(courseId: Long) {
        scheduleDao.deleteByCourseId(courseId)
        courseDao.getById(courseId)?.let { courseDao.delete(it) }
    }

    @Transaction
    suspend fun insertCourseWithSchedule(
        course: Course,
        schedule: Schedule
    ): Long {
        val courseId = courseDao.insert(course)
        scheduleDao.insert(schedule.copy(courseId = courseId))
        return courseId
    }
}