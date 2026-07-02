package com.example.courseschedule.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.courseschedule.data.db.dao.*
import com.example.courseschedule.data.db.entity.*

@Database(
    entities = [Semester::class, Course::class, Schedule::class, Room::class, Exam::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun courseDao(): CourseDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun roomDao(): RoomDao
    abstract fun examDao(): ExamDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS exams_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        courseId INTEGER NOT NULL,
                        examDate INTEGER NOT NULL,
                        reminderHours INTEGER NOT NULL DEFAULT 48,
                        notes TEXT,
                        FOREIGN KEY (courseId) REFERENCES courses(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("""
                    INSERT INTO exams_new (id, courseId, examDate, reminderHours, notes)
                    SELECT id, courseId, examDate, 48, notes FROM exams
                """)
                db.execSQL("DROP TABLE exams")
                db.execSQL("ALTER TABLE exams_new RENAME TO exams")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE semesters ADD COLUMN weekDays INTEGER NOT NULL DEFAULT 5")
            }
        }
    }
}