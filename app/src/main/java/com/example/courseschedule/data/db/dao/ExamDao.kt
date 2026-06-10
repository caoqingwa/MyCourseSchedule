package com.example.courseschedule.data.db.dao

import androidx.room.*
import com.example.courseschedule.data.db.entity.Exam
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE courseId IN (SELECT id FROM courses WHERE semesterId = :semesterId) ORDER BY examDate")
    fun getBySemester(semesterId: Long): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun getById(id: Long): Exam?

    @Query("SELECT * FROM exams WHERE examDate < :nowMillis")
    suspend fun getExpired(nowMillis: Long): List<Exam>

    @Query("DELETE FROM exams WHERE examDate < :nowMillis")
    suspend fun deleteExpired(nowMillis: Long)

    @Insert
    suspend fun insert(exam: Exam): Long

    @Update
    suspend fun update(exam: Exam)

    @Delete
    suspend fun delete(exam: Exam)
}