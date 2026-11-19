package com.example.courseschedule.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "schedule_changes",
    primaryKeys = ["scheduleId", "originalDay"],
    foreignKeys = [ForeignKey(
        entity = Schedule::class,
        parentColumns = ["id"],
        childColumns = ["scheduleId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ScheduleChange(
    val scheduleId: Long,
    val originalDay: Long,
    val targetDay: Long?,
    val startPeriod: Int,
    val endPeriod: Int,
    val roomName: String?,
    val keepOriginal: Boolean = false
)
