package com.example.courseschedule.data.db.dao

import androidx.room.*
import com.example.courseschedule.data.db.entity.Semester
import kotlinx.coroutines.flow.Flow

@Dao
interface SemesterDao {
  
    fun getAll(): Flow<List<Semester>>

    @Query("SELECT * FROM semesters WHERE id = :id")
    suspend fun getById(id: Long): Semester?

    @Query("SELECT * FROM semesters ORDER BY startDate DESC LIMIT 1")
    fun getCurrent(): Flow<Semester?>

    @Insert
    suspend fun insert(semester: Semester): Long

    @Update
    suspend fun update(semester: Semester)

    @Delete
    suspend fun delete(semester: Semester)
}
