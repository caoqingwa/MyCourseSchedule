package com.example.courseschedule.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "exams",
    foreignKeys = [ForeignKey(
        entity = Course::class,
        parentColumns = ["id"],
        childColumns = ["courseId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val examDate: Long,
    val reminderDays: Int = 3,
    val notes: String? = null
)
