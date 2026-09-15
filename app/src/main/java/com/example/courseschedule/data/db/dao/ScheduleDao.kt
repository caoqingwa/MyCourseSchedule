package com.example.courseschedule.data.db.dao

import androidx.room.*
import com.example.courseschedule.data.db.entity.Schedule
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT s.* FROM schedules s INNER JOIN courses c ON s.courseId = c.id WHERE c.semesterId = :semesterId")
    fun getBySemester(semesterId: Long): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE courseId = :courseId")
    suspend fun getByCourse(courseId: Long): List<Schedule>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getById(id: Long): Schedule?

    /** 查重：同一课程同一天/节次/周次/周型的时段是否已存在（导入幂等） */
    @Query(
        """SELECT * FROM schedules WHERE courseId = :courseId AND dayOfWeek = :dayOfWeek
           AND startPeriod = :startPeriod AND endPeriod = :endPeriod
           AND startWeek = :startWeek AND endWeek = :endWeek AND weekType = :weekType LIMIT 1"""
    )
    suspend fun findDuplicate(
        courseId: Long, dayOfWeek: Int, startPeriod: Int, endPeriod: Int,
        startWeek: Int, endWeek: Int, weekType: Int
    ): Schedule?

    @Insert
    suspend fun insert(schedule: Schedule): Long

    @Update
    suspend fun update(schedule: Schedule)

    @Delete
    suspend fun delete(schedule: Schedule)

    @Query("DELETE FROM schedules WHERE courseId = :courseId")
    suspend fun deleteByCourseId(courseId: Long)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()
}
