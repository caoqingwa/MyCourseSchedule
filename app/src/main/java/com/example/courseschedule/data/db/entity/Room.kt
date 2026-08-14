package com.example.courseschedule.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "rooms",
    indices = [Index(value = ["name"], unique = true)]
)
data class Room(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val building: String? = null
)