package com.example.courseschedule.data.db.dao

import androidx.room.*
import com.example.courseschedule.data.db.entity.Exam
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {
    @Query("SELECT e.* FROM exams e INNER JOIN courses c ON e.courseId = c.id WHERE c.semesterId = :semesterId ORDER BY e.examDate")
    fun getBySemester(semesterId: Long): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun getById(id: Long): Exam?

    @Query("SELECT * FROM exams WHERE examDate < :nowMillis")
    suspend fun getExpired(nowMillis: Long): List<Exam>

    @Query("SELECT * FROM exams WHERE examDate > :nowMillis ORDER BY examDate")
    suspend fun getAllPending(nowMillis: Long): List<Exam>

    @Query("DELETE FROM exams WHERE examDate < :nowMillis")
    suspend fun deleteExpired(nowMillis: Long)

    @Insert
    suspend fun insert(exam: Exam): Long

    @Update
    suspend fun update(exam: Exam)

    @Delete
    suspend fun delete(exam: Exam)
}