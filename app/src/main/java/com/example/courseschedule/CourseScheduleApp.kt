package com.example.courseschedule

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CourseScheduleApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val courseChannel = NotificationChannel(
            "course_reminder",
            "\u8bfe\u7a0b\u63d0\u9192",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "\u8bfe\u7a0b\u5f00\u59cb\u524d\u63d0\u9192" }

        val examChannel = NotificationChannel(
            "exam_reminder",
            "\u8003\u8bd5\u63d0\u9192",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "\u8003\u8bd5\u65e5\u671f\u63d0\u9192" }

        val dailyChannel = NotificationChannel(
            "daily_summary",
            "\u6bcf\u65e5\u8bfe\u7a0b",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "\u6bcf\u65e5\u8bfe\u7a0b\u6c47\u603b" }

        manager.createNotificationChannels(listOf(courseChannel, examChannel, dailyChannel))
    }
}