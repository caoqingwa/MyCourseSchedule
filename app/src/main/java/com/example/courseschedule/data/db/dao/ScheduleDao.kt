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
