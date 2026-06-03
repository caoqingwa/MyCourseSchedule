package com.example.courseschedule

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CourseScheduleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val courseChannel = NotificationChannel(
            "course_reminder",
            "课程提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "课程开始前提醒" }

        val examChannel = NotificationChannel(
            "exam_reminder",
            "考试提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "考试日期提醒" }

        val dailyChannel = NotificationChannel(
            "daily_summary",
            "每日课程",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "每日课程汇总" }

        manager.createNotificationChannels(listOf(courseChannel, examChannel, dailyChannel))
    }
}
