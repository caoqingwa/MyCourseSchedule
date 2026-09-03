package com.example.courseschedule.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "courses",
    foreignKeys = [ForeignKey(
        entity = Semester::class,
        parentColumns = ["id"],
        childColumns = ["semesterId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("semesterId")]
)
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val semesterId: Long,
    val name: String,
    val teacher: String,
    val color: String,
    /** 遗留字段：v8 起教室归属 schedule.roomId（同课程不同时段可不同教室），本字段仅保持表结构兼容，不再写入/读取 */
    val roomId: Long? = null
)