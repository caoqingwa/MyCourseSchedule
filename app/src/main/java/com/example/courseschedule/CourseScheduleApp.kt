package com.example.courseschedule

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.courseschedule.data.repository.CourseRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CourseScheduleApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var repository: CourseRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        appScope.launch { repository.initDefaultSemester() }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val courseChannel = NotificationChannel("course_reminder", "\u8bfe\u7a0b\u63d0\u9192", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "\u8bfe\u7a0b\u5f00\u59cb\u524d\u63d0\u9192" }
        val examChannel = NotificationChannel("exam_reminder", "\u8003\u8bd5\u63d0\u9192", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "\u8003\u8bd5\u65e5\u671f\u63d0\u9192" }
        val dailyChannel = NotificationChannel("daily_summary", "\u6bcf\u65e5\u8bfe\u7a0b", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "\u6bcf\u65e5\u8bfe\u7a0b\u6c47\u603b" }
        manager.createNotificationChannels(listOf(courseChannel, examChannel, dailyChannel))
    }
}
