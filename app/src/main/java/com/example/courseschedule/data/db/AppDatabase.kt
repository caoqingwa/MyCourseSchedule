package com.example.courseschedule.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.courseschedule.data.db.dao.*
import com.example.courseschedule.data.db.entity.*

@Database(
    entities = [Semester::class, Course::class, Schedule::class, Room::class, Exam::class],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun courseDao(): CourseDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun roomDao(): RoomDao
    abstract fun examDao(): ExamDao

    companion object {
        // v1 与 v2 实体结构相同，仅补链，保证老用户可从版本 1 平滑升级
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

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
                // v1/v2 的提醒单位为天，按 24 小时换算保留用户配置（NULL 视为默认 2 天）
                db.execSQL("""
                    INSERT INTO exams_new (id, courseId, examDate, reminderHours, notes)
                    SELECT id, courseId, examDate, COALESCE(reminderDays, 2) * 24, notes FROM exams
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_courses_semesterId ON courses(semesterId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_schedules_courseId ON schedules(courseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exams_courseId ON exams(courseId)")
            }
        }

        // 修复早期 v5 构建留下的错误索引名 (idx_*)，并确保正确的 Room 索引存在
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS idx_courses_semester")
                db.execSQL("DROP INDEX IF EXISTS idx_schedules_course")
                db.execSQL("DROP INDEX IF EXISTS idx_exams_course")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_courses_semesterId ON courses(semesterId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_schedules_courseId ON schedules(courseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exams_courseId ON exams(courseId)")
            }
        }

        // rooms.name 加唯一约束：先合并重复行（保留最小 id），再建唯一索引
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM rooms WHERE id NOT IN (SELECT MIN(id) FROM rooms GROUP BY name)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rooms_name ON rooms(name)")
            }
        }
    }
}