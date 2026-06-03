package com.example.courseschedule.data.db.dao

import androidx.room.*
import com.example.courseschedule.data.db.entity.Schedule
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE courseId IN (SELECT id FROM courses WHERE semesterId = :semesterId)")
    fun getBySemester(semesterId: Long): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE courseId = :courseId")
    fun getByCourse(courseId: Long): Flow<List<Schedule>>

    @Insert
    suspend fun insert(schedule: Schedule): Long

    @Update
    suspend fun update(schedule: Schedule)

    @Delete
    suspend fun delete(schedule: Schedule)
}
