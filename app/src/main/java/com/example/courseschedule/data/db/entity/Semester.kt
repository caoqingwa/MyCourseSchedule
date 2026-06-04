package com.example.courseschedule.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "semesters")
data class Semester(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: Long,
    val totalWeeks: Int,
    val periodCount: Int = 12,
    val periodTimesJson: String = DEFAULT_PERIOD_TIMES_JSON
) {
    data class PeriodTime(val start: String, val end: String)

    fun getPeriodTimes(): List<PeriodTime> {
        return try {
            val arr = JSONArray(periodTimesJson)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PeriodTime(obj.getString("start"), obj.getString("end"))
            }
        } catch (e: Exception) {
            defaultPeriodTimes()
        }
    }

    fun getStartTimes(): List<String> = getPeriodTimes().map { it.start }
    fun getEndTimes(): List<String> = getPeriodTimes().map { it.end }

    companion object {
        fun defaultPeriodTimes(): List<PeriodTime> = listOf(
            PeriodTime("08:00", "08:45"), PeriodTime("08:55", "09:40"),
            PeriodTime("10:00", "10:45"), PeriodTime("10:55", "11:40"),
            PeriodTime("14:00", "14:45"), PeriodTime("14:55", "15:40"),
            PeriodTime("16:00", "16:45"), PeriodTime("16:55", "17:40"),
            PeriodTime("19:00", "19:45"), PeriodTime("19:55", "20:40"),
            PeriodTime("20:50", "21:35"), PeriodTime("21:45", "22:30")
        )

        fun buildPeriodTimesJson(times: List<PeriodTime>): String {
            val arr = JSONArray()
            for (t in times) {
                val obj = JSONObject()
                obj.put("start", t.start)
                obj.put("end", t.end)
                arr.put(obj)
            }
            return arr.toString()
        }

        val DEFAULT_PERIOD_TIMES_JSON: String by lazy {
            buildPeriodTimesJson(defaultPeriodTimes())
        }
    }
}
