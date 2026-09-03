package com.example.courseschedule.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.courseschedule.data.db.dao.*
import com.example.courseschedule.data.db.entity.*

@Database(
    entities = [Semester::class, Course::class, Schedule::class, Room::class, Exam::class],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun courseDao(): CourseDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun roomDao(): RoomDao
    abstract fun examDao(): ExamDao

    companion object {
        // v1→v2：semesters 表新增 periodCount 与 periodTimesJson 两列（NOT NULL 需带默认值）
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE semesters ADD COLUMN periodCount INTEGER NOT NULL DEFAULT 12")
                db.execSQL("""
                    ALTER TABLE semesters ADD COLUMN periodTimesJson TEXT NOT NULL DEFAULT
                    '[{"start":"08:00","end":"08:45"},{"start":"08:55","end":"09:40"},{"start":"10:00","end":"10:45"},{"start":"10:55","end":"11:40"},{"start":"14:00","end":"14:45"},{"start":"14:55","end":"15:40"},{"start":"16:00","end":"16:45"},{"start":"16:55","end":"17:40"},{"start":"19:00","end":"19:45"},{"start":"19:55","end":"20:40"},{"start":"20:50","end":"21:35"},{"start":"21:45","end":"22:30"}]'
                """)
            }
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

        // rooms.name 加唯一约束：先把引用"将被删除的重复行"的课程重定向到同名保留行，再删重复，最后建唯一索引
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 引用将被删除行的课程，改为指向同名的保留行（MIN id）
                db.execSQL("""
                    UPDATE courses SET roomId = (
                        SELECT MIN(id) FROM rooms r2 WHERE r2.name = (
                            SELECT name FROM rooms WHERE id = courses.roomId
                        )
                    ) WHERE roomId IN (
                        SELECT id FROM rooms WHERE id NOT IN (SELECT MIN(id) FROM rooms GROUP BY name)
                    )
                """)
                db.execSQL("DELETE FROM rooms WHERE id NOT IN (SELECT MIN(id) FROM rooms GROUP BY name)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rooms_name ON rooms(name)")
            }
        }

        // 教室从课程级（courses.roomId）迁移到时段级（schedules.roomId）：
        // 同一课程不同时段可以有不同教室。先把已有课程教室回填到其全部时段，
        // courses.roomId 列保留（旧版本实体一致），但不再作为权威来源。
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN roomId INTEGER")
                db.execSQL("""
                    UPDATE schedules SET roomId = (
                        SELECT roomId FROM courses WHERE courses.id = schedules.courseId
                    )
                """)
            }
        }
    }
}