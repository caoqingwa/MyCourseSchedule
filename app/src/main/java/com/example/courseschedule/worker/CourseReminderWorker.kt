package com.example.courseschedule.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.courseschedule.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class CourseReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val courseName = inputData.getString("course_name") ?: return Result.failure()
        val roomName = inputData.getString("room_name") ?: ""
        val period = inputData.getString("period") ?: ""
        NotificationHelper.showCourseReminder(applicationContext, courseName, roomName, period)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, courseName: String, roomName: String, period: String, delayMinutes: Long) {
            val data = workDataOf("course_name" to courseName, "room_name" to roomName, "period" to period)
            val request = OneTimeWorkRequestBuilder<CourseReminderWorker>()
                .setInputData(data)
                .setDelay(delayMinutes, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
