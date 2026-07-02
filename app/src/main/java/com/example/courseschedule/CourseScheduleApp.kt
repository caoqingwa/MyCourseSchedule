package com.example.courseschedule

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.courseschedule.data.db.dao.ExamDao
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.worker.ExamReminderWorker
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
    @Inject lateinit var examDao: ExamDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        appScope.launch {
            repository.initDefaultSemester()
            ExamReminderWorker.rescheduleAll(applicationContext, examDao)
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val courseChannel = NotificationChannel("course_reminder", "\u8bfe\u7a0b\u63d0\u9192", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "\u8bfe\u7a0b\u5f00\u59cb\u524d\u63d0\u9192" }
        val examChannel = NotificationChannel("exam_reminder", "\u8003\u8bd5\u63d0\u9192", NotificationManager.IMPORTANCE_HIGH)
            .apply {
                description = "\u8003\u8bd5\u65e5\u671f\u63d0\u9192"
                enableVibration(true)
            }
        manager.createNotificationChannels(listOf(courseChannel, examChannel))
    }
}
