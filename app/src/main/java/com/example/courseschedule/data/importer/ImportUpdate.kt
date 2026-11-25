package com.example.courseschedule.data.importer

import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule

sealed interface ImportUpdate {
    val description: String

    data class Teacher(
        val course: Course,
        val newName: String
    ) : ImportUpdate {
        override val description: String
            get() = "${course.name} · 教师：${course.teacher.ifBlank { "未填写" }} → $newName"
    }

    data class Room(
        val course: Course,
        val schedule: Schedule,
        val oldName: String,
        val newName: String,
        val sourceRow: Int
    ) : ImportUpdate {
        override val description: String
            get() = "第 $sourceRow 行 · ${course.name} · 教室：${oldName.ifBlank { "未填写" }} → $newName"
    }
}

data class CourseImportResult(val courseCount: Int, val scheduleCount: Int, val updateCount: Int)
