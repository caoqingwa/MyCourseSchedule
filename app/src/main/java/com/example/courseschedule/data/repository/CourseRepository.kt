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
    suspend fun getSchedulesByCourse(courseId: Long): List<Schedule> = scheduleDao.getByCourse(courseId)
    suspend fun insertSchedule(schedule: Schedule): Long = scheduleDao.insert(schedule)
    suspend fun updateSchedule(schedule: Schedule) = scheduleDao.update(schedule)
    suspend fun deleteSchedule(schedule: Schedule) = scheduleDao.delete(schedule)
    suspend fun deleteSchedulesByCourseId(courseId: Long) = scheduleDao.deleteByCourseId(courseId)

    /** 同名教室复用；并发/重复插入由唯一索引兜底（IGNORE 后回查） */
    suspend fun insertRoom(room: Room): Long {
        roomDao.getByName(room.name)?.let { return it.id }
        val id = roomDao.insert(room)
        return if (id != -1L) id else roomDao.getByName(room.name)?.id ?: -1L
    }
    fun getAllRooms(): Flow<List<Room>> = roomDao.getAll()
    suspend fun getAllRoomsOnce(): List<Room> = roomDao.getAllOnce()
    suspend fun getRoomById(id: Long): Room? = roomDao.getById(id)

    /** 改名教室；若新名字已被其他教室占用则直接复用该教室 id，避免 UNIQUE 冲突 */
    suspend fun updateRoom(roomId: Long, newName: String): Long {
        roomDao.getByName(newName)?.let { return it.id }
        roomDao.getById(roomId)?.let { r: Room -> roomDao.update(r.copy(name = newName)) }
        return roomId
    }

    fun getExamsBySemester(semesterId: Long): Flow<List<Exam>> = examDao.getBySemester(semesterId)
    fun getExamDao(): ExamDao = examDao
    suspend fun insertExam(exam: Exam): Long = examDao.insert(exam)
    suspend fun updateExam(exam: Exam) = examDao.update(exam)
    suspend fun deleteExam(exam: Exam) = examDao.delete(exam)
    suspend fun deleteExpiredExams() = examDao.deleteExpired(System.currentTimeMillis())

    /** 删除单个排课时段；若该课程已无其他时段则连同课程一并删除，并回收空教室 */
    @Transaction
    suspend fun deleteScheduleWithCourseIfOrphan(scheduleId: Long) {
        val schedule = scheduleDao.getById(scheduleId) ?: return
        scheduleDao.delete(schedule)
        if (scheduleDao.getByCourse(schedule.courseId).isEmpty()) {
            courseDao.getById(schedule.courseId)?.let { courseDao.delete(it) }
        }
        roomDao.deleteUnused()
    }

    /** 回收不再被任何时段引用的教室（编辑清空教室后调用） */
    suspend fun deleteUnusedRooms() = roomDao.deleteUnused()

    @Transaction
    suspend fun insertCourseWithSchedule(
        course: Course,
        schedule: Schedule
    ): Long {
        val courseId = courseDao.insert(course)
        scheduleDao.insert(schedule.copy(courseId = courseId))
        return courseId
    }

    /** 清除所有课程相关数据（课程/课表/考试/教室），保留学期设置 */
    @Transaction
    suspend fun clearAllCourseData() {
        scheduleDao.deleteAll()
        examDao.deleteAll()
        courseDao.deleteAll()
        roomDao.deleteAll()
    }

    /** 批量导入（幂等）：学期内同名课程复用，已存在的同课程同时段跳过；每时段独立解析并复用同名教室 */
    @Transaction
    suspend fun importCourseWithSchedules(
        semesterId: Long,
        name: String,
        teacher: String,
        schedules: List<Pair<Schedule, String?>>
    ): Long {
        val courseId = courseDao.getByNameInSemester(semesterId, name)?.id
            ?: courseDao.insert(Course(semesterId = semesterId, name = name, teacher = teacher, color = "0"))
        for ((sched, roomName) in schedules) {
            val duplicate = scheduleDao.findDuplicate(
                courseId = courseId, dayOfWeek = sched.dayOfWeek,
                startPeriod = sched.startPeriod, endPeriod = sched.endPeriod,
                startWeek = sched.startWeek, endWeek = sched.endWeek, weekType = sched.weekType
            )
            if (duplicate != null) continue
            val roomId = roomName?.takeIf { it.isNotBlank() }?.let { insertRoom(Room(name = it)) }
            scheduleDao.insert(sched.copy(courseId = courseId, roomId = roomId))
        }
        return courseId
    }
}