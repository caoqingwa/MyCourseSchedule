package com.example.courseschedule.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.courseschedule.data.db.dao.*
import com.example.courseschedule.data.db.entity.*

@Database(
    entities = [Semester::class, Course::class, Schedule::class, Room::class, Exam::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun courseDao(): CourseDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun roomDao(): RoomDao
    abstract fun examDao(): ExamDao
}