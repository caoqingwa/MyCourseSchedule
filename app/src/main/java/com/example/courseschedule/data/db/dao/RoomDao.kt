package com.example.courseschedule.data.db.dao

import androidx.room.*
import com.example.courseschedule.data.db.entity.Room
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY name")
    fun getAll(): Flow<List<Room>>

    @Query("SELECT * FROM rooms ORDER BY name")
    suspend fun getAllOnce(): List<Room>

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun getById(id: Long): Room?

    @Query("SELECT * FROM rooms WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Room?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(room: Room): Long

    /** 删除已无任何时段引用的教室（清空/改名/删除时段后回收） */
    @Query("DELETE FROM rooms WHERE id NOT IN (SELECT roomId FROM schedules WHERE roomId IS NOT NULL)")
    suspend fun deleteUnused()

    @Update
    suspend fun update(room: Room)

    @Delete
    suspend fun delete(room: Room)

    @Query("DELETE FROM rooms")
    suspend fun deleteAll()
}
