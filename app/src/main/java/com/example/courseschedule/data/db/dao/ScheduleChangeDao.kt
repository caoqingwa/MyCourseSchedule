package com.example.courseschedule.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.courseschedule.data.db.entity.ScheduleChange
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleChangeDao {
    @Query(
        """SELECT sc.* FROM schedule_changes sc
           JOIN schedules s ON s.id = sc.scheduleId
           JOIN courses c ON c.id = s.courseId
           WHERE c.semesterId = :semesterId"""
    )
    fun observeBySemester(semesterId: Long): Flow<List<ScheduleChange>>

    @Query("SELECT * FROM schedule_changes WHERE scheduleId IN (:scheduleIds)")
    suspend fun getBySchedules(scheduleIds: List<Long>): List<ScheduleChange>

    @Upsert
    suspend fun save(change: ScheduleChange)

    @Query("DELETE FROM schedule_changes WHERE scheduleId = :scheduleId AND originalDay = :originalDay")
    suspend fun remove(scheduleId: Long, originalDay: Long)
}
