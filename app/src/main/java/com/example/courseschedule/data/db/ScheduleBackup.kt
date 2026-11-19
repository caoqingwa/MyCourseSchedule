package com.example.courseschedule.data.db

import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalTime

class ScheduleBackup private constructor(private val tables: JSONObject) {
    val semesterNames: List<String>
        get() = tables.getJSONArray("semesters").let { rows ->
            (0 until rows.length()).map { rows.getJSONArray(it).getString(1) }
        }
    val courseCount: Int get() = tables.getJSONArray("courses").length()
    val examCount: Int get() = tables.getJSONArray("exams").length()
    val changeCount: Int get() = tables.getJSONArray("schedule_changes").length()

    fun encode(): String = JSONObject().put("format", 1).put("tables", tables).toString()

    suspend fun restore(database: AppDatabase) = database.withTransaction {
        val sqlite = database.openHelper.writableDatabase
        for (table in columns.keys.reversed()) sqlite.execSQL("DELETE FROM $table")
        for ((table, fields) in columns) {
            val rows = tables.getJSONArray(table)
            val placeholders = fields.joinToString(",") { "?" }
            val insert = "INSERT INTO $table (${fields.joinToString(",")}) VALUES ($placeholders)"
            for (i in 0 until rows.length()) {
                val row = rows.getJSONArray(i)
                val args = Array<Any?>(fields.size) { index -> row.get(index).takeUnless { it == JSONObject.NULL } }
                sqlite.execSQL(insert, args)
            }
        }
        sqlite.query("PRAGMA foreign_key_check").use {
            require(!it.moveToFirst()) { "备份中的数据关联不完整" }
        }
        sqlite.query(
            """SELECT 1 FROM schedules s
                LEFT JOIN rooms r ON r.id = s.roomId
                WHERE s.roomId IS NOT NULL AND r.id IS NULL LIMIT 1"""
        ).use {
            require(!it.moveToFirst()) { "备份中的排课引用了不存在的教室" }
        }
    }

    companion object {
        private val columns = linkedMapOf(
            "semesters" to listOf("id", "name", "startDate", "totalWeeks", "periodCount", "weekDays", "periodTimesJson"),
            "rooms" to listOf("id", "name", "building"),
            "courses" to listOf("id", "semesterId", "name", "teacher", "color", "roomId"),
            "schedules" to listOf("id", "courseId", "dayOfWeek", "startPeriod", "endPeriod", "startWeek", "endWeek", "weekType", "roomId"),
            "exams" to listOf("id", "courseId", "examDate", "reminderHours", "notes"),
            "schedule_changes" to listOf("scheduleId", "originalDay", "targetDay", "startPeriod", "endPeriod", "roomName", "keepOriginal")
        )
        private const val MAX_BYTES = 8 * 1024 * 1024
        private val textFields = setOf("name", "periodTimesJson", "building", "teacher", "color", "notes", "roomName")
        private val nullableFields = setOf("building", "roomId", "notes", "targetDay", "roomName")

        fun read(input: InputStream): ScheduleBackup {
            val bytes = input.readNBytesCompat()
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            require(root.getInt("format") == 1) { "不支持此备份格式" }
            val tables = root.getJSONObject("tables")
            for ((table, fields) in columns) {
                val rows = tables.getJSONArray(table)
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONArray(i)
                    require(row.length() == fields.size) { "备份字段不完整" }
                    for (index in fields.indices) {
                        val field = fields[index]
                        val value = row.get(index)
                        val valid = when {
                            value == JSONObject.NULL -> field in nullableFields
                            field in textFields -> value is String
                            else -> value is Int || value is Long
                        }
                        require(valid) { "备份字段无效：$table.$field" }
                    }
                    validateRow(table, row)
                }
            }
            require(tables.getJSONArray("semesters").length() > 0) { "备份中没有学期" }
            return ScheduleBackup(tables)
        }

        private fun validateRow(table: String, row: JSONArray) {
            when (table) {
                "semesters" -> {
                    require(row.getLong(3) in 1L..30L && row.getLong(4) in 1L..24L &&
                        row.getLong(5) in setOf(5L, 7L)) { "备份中的学期设置无效" }
                    val times = JSONArray(row.getString(6))
                    for (index in 0 until times.length()) {
                        val period = times.getJSONObject(index)
                        LocalTime.parse(period.getString("start"))
                        LocalTime.parse(period.getString("end"))
                    }
                }
                "schedules" -> require(
                    row.getLong(2) in 1L..7L && row.getLong(3) in 1L..24L &&
                        row.getLong(4) in row.getLong(3)..24L &&
                        row.getLong(5) in 1L..30L && row.getLong(6) in row.getLong(5)..30L &&
                        row.getLong(7) in 0L..2L
                ) { "备份中的排课范围无效" }
                "exams" -> require(row.getLong(3) in 0L..Int.MAX_VALUE.toLong()) {
                    "备份中的考试提前提醒时间无效"
                }
                "schedule_changes" -> {
                    LocalDate.ofEpochDay(row.getLong(1))
                    if (!row.isNull(2)) LocalDate.ofEpochDay(row.getLong(2))
                    require(row.getLong(3) in 1L..24L && row.getLong(4) in row.getLong(3)..24L &&
                        row.getLong(6) in 0L..1L) { "备份中的单次调课无效" }
                }
            }
        }

        private fun InputStream.readNBytesCompat(): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_BYTES) { "备份文件超过 8 MB" }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }

        suspend fun capture(database: AppDatabase): ScheduleBackup = database.withTransaction {
            val tables = JSONObject()
            for ((table, fields) in columns) {
                val rows = JSONArray()
                database.query(SimpleSQLiteQuery("SELECT ${fields.joinToString(",")} FROM $table")).use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = JSONArray()
                        for (index in fields.indices) {
                            row.put(when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                                else -> cursor.getString(index)
                            })
                        }
                        rows.put(row)
                    }
                }
                tables.put(table, rows)
            }
            ScheduleBackup(tables)
        }
    }
}
