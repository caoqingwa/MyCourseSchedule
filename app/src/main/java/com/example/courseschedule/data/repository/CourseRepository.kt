package com.example.courseschedule.data.repository

import com.example.courseschedule.data.db.dao.*
import com.example.courseschedule.data.db.entity.*
import kotlinx.coroutines.flow.Flow
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
    suspend fun insertSemester(semester: Semester): Long = semesterDao.insert(semester)

    fun getCoursesBySemester(semesterId: Long): Flow<List<Course>> = courseDao.getBySemester(semesterId)
    suspend fun getCourseById(id: Long): Course? = courseDao.getById(id)
    suspend fun insertCourse(course: Course): Long = courseDao.insert(course)
    suspend fun updateCourse(course: Course) = courseDao.update(course)
    suspend fun deleteCourse(course: Course) = courseDao.delete(course)

    fun getSchedulesBySemester(semesterId: Long): Flow<List<Schedule>> = scheduleDao.getBySemester(semesterId)
    suspend fun insertSchedule(schedule: Schedule): Long = scheduleDao.insert(schedule)

    fun getExamsBySemester(semesterId: Long): Flow<List<Exam>> = examDao.getBySemester(semesterId)
    suspend fun insertExam(exam: Exam): Long = examDao.insert(exam)
}
