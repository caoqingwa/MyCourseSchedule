package com.example.courseschedule.data.repository

import androidx.room.Transaction
import androidx.room.withTransaction
import com.example.courseschedule.data.db.AppDatabase
import com.example.courseschedule.data.db.ScheduleBackup
import com.example.courseschedule.data.db.dao.*
import com.example.courseschedule.data.db.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.courseschedule.util.SettingsPrefs
import com.example.courseschedule.util.DateUtils
import java.time.Instant
import java.time.ZoneId
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepository @Inject constructor(
    private val database: AppDatabase,
    private val semesterDao: SemesterDao,
    private val courseDao: CourseDao,
    private val scheduleDao: ScheduleDao,
    private val roomDao: RoomDao,
    private val examDao: ExamDao
) {
    fun getCurrentSemester(): Flow<Semester?> = combine(semesterDao.getAll(), SettingsPrefs.selectedSemesterId) { semesters, id ->
        semesters.firstOrNull { it.id == id } ?: semesters.firstOrNull()
    }.distinctUntilChanged()
    suspend fun captureBackup(): ScheduleBackup = ScheduleBackup.capture(database)
    suspend fun restoreBackup(backup: ScheduleBackup) {
        backup.restore(database)
        SettingsPrefs.selectSemester(0L)
    }
    fun getScheduleChanges(semesterId: Long): Flow<List<ScheduleChange>> =
        database.scheduleChangeDao().observeBySemester(semesterId)

    suspend fun saveScheduleChange(change: ScheduleChange) = database.scheduleChangeDao().save(change)

    suspend fun removeScheduleChange(scheduleId: Long, originalDay: Long) =
        database.scheduleChangeDao().remove(scheduleId, originalDay)
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

    fun getExamsBySemester(semesterId: Long): Flow<List<Exam>> = examDao.getBySemester(semesterId)
    fun getExamDao(): ExamDao = examDao
    suspend fun insertExam(exam: Exam): Long = examDao.insert(exam)
    suspend fun updateExam(exam: Exam) = examDao.update(exam)
    suspend fun deleteExam(exam: Exam) = examDao.delete(exam)
    suspend fun deleteExpiredExams(beforeMillis: Long) = examDao.deleteExpired(beforeMillis)

    /** 删除单个排课时段；若该课程已无其他时段则连同课程一并删除，并回收空教室 */
    @Transaction
    suspend fun deleteScheduleWithCourseIfOrphan(scheduleId: Long) {
        val schedule = scheduleDao.getById(scheduleId) ?: return
        val group = scheduleDao.getByCourse(schedule.courseId).filter {
            it.dayOfWeek == schedule.dayOfWeek && it.startPeriod == schedule.startPeriod &&
                it.endPeriod == schedule.endPeriod && it.roomId == schedule.roomId
        }
        group.forEach { scheduleDao.delete(it) }
        if (scheduleDao.getByCourse(schedule.courseId).isEmpty()) {
            courseDao.getById(schedule.courseId)?.let { courseDao.delete(it) }
        }
        roomDao.deleteUnused()
    }

    /** 回收不再被任何时段引用的教室（编辑清空教室后调用） */
    suspend fun deleteUnusedRooms() = roomDao.deleteUnused()

    @Transaction
    suspend fun insertCourseWithSchedules(
        course: Course,
        schedules: List<Schedule>
    ): Long {
        val courseId = courseDao.insert(course)
        for (schedule in schedules) {
            scheduleDao.insert(schedule.copy(courseId = courseId))
        }
        return courseId
    }

    suspend fun replaceSchedules(
        course: Course,
        oldSchedules: List<Schedule>,
        newSchedules: List<Schedule>,
        roomName: String
    ) = database.withTransaction {
        val semester = semesterDao.getById(course.semesterId) ?: return@withTransaction
        val start = Instant.ofEpochMilli(semester.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
        val firstDay = start.toEpochDay() - start.dayOfWeek.value + 1
        val changesDao = database.scheduleChangeDao()
        val changes = changesDao.getBySchedules(oldSchedules.map { it.id })
        val roomId = roomName.trim().takeIf { it.isNotEmpty() }?.let {
            insertRoom(Room(name = it)).also { id -> check(id > 0) { "无法保存教室" } }
        }
        courseDao.update(course)
        oldSchedules.forEach { scheduleDao.delete(it) }
        val replacements = newSchedules.map {
            it to scheduleDao.insert(it.copy(courseId = course.id, roomId = roomId))
        }
        for (change in changes) {
            val offset = change.originalDay - firstDay
            if (offset !in 0L until semester.totalWeeks.toLong() * 7) continue
            val week = (offset / 7).toInt() + 1
            val (replacement, id) = replacements.firstOrNull { (schedule, _) ->
                DateUtils.isScheduleActive(schedule.startWeek, schedule.endWeek, schedule.weekType, week)
            } ?: continue
            val originalDay = firstDay + (week - 1) * 7L + replacement.dayOfWeek - 1
            changesDao.save(change.copy(scheduleId = id, originalDay = originalDay))
        }
        roomDao.deleteUnused()
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
    suspend fun importCourses(
        semesterId: Long,
        courses: List<Triple<String, String, List<Pair<Schedule, String?>>>>
    ): Pair<Int, Int> = database.withTransaction {
        var coursesImported = 0
        var schedulesImported = 0
        for ((name, teacher, schedules) in courses) {
            val added = importCourseWithSchedules(semesterId, name, teacher, schedules)
            if (added > 0) coursesImported++
            schedulesImported += added
        }
        coursesImported to schedulesImported
    }

    private suspend fun importCourseWithSchedules(
        semesterId: Long,
        name: String,
        teacher: String,
        schedules: List<Pair<Schedule, String?>>
    ): Int {
        val courseId = courseDao.getByNameInSemester(semesterId, name)?.id
            ?: courseDao.insert(Course(semesterId = semesterId, name = name, teacher = teacher, color = "0"))
        var added = 0
        for ((sched, roomName) in schedules) {
            val duplicate = scheduleDao.findDuplicate(
                courseId = courseId, dayOfWeek = sched.dayOfWeek,
                startPeriod = sched.startPeriod, endPeriod = sched.endPeriod,
                startWeek = sched.startWeek, endWeek = sched.endWeek, weekType = sched.weekType
            )
            if (duplicate != null) continue
            val roomId = roomName?.takeIf { it.isNotBlank() }?.let { insertRoom(Room(name = it)) }
            scheduleDao.insert(sched.copy(courseId = courseId, roomId = roomId))
            added++
        }
        return added
    }
}
