package com.example.courseschedule.data.db.dao

import androidx.room.*
import com.example.courseschedule.data.db.entity.Semester
import kotlinx.coroutines.flow.Flow

@Dao
interface SemesterDao {
    @Query("SELECT * FROM semesters ORDER BY startDate DESC")
    fun getAll(): Flow<List<Semester>>

    @Query("SELECT * FROM semesters ORDER BY startDate DESC")
    suspend fun getAllSync(): List<Semester>

    @Query("SELECT * FROM semesters WHERE id = :id")
    suspend fun getById(id: Long): Semester?

    @Query("SELECT * FROM semesters ORDER BY startDate DESC LIMIT 1")
    fun getCurrent(): Flow<Semester?>

    @Query("SELECT COUNT(*) FROM semesters")
    suspend fun getCount(): Int

    @Insert
    suspend fun insert(semester: Semester): Long

    @Update
    suspend fun update(semester: Semester)

    @Delete
    suspend fun delete(semester: Semester)

    @Query("DELETE FROM semesters WHERE id = :id")
    suspend fun deleteById(id: Long)
}
