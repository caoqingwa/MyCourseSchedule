package com.example.courseschedule.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "courses",
    foreignKeys = [ForeignKey(
        entity = Semester::class,
        parentColumns = ["id"],
        childColumns = ["semesterId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val semesterId: Long,
    val name: String,
    val teacher: String,
    val color: String,
    val roomId: Long? = null
)