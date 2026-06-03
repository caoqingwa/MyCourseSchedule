package com.example.courseschedule.data.db.dao

import androidx.room.*
import com.example.courseschedule.data.db.entity.Course
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE semesterId = :semesterId ORDER BY name")
    fun getBySemester(semesterId: Long): Flow<List<Course>>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getById(id: Long): Course?

    @Insert
    suspend fun insert(course: Course): Long

    @Update
    suspend fun update(course: Course)

    @Delete
    suspend fun delete(course: Course)
}
