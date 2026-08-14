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
        val scheduleId = inputData.getLong("schedule_id", -1L)
        NotificationHelper.showCourseReminder(applicationContext, courseName, roomName, period, scheduleId.toInt())
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PREFIX = "course_reminder_"

        fun schedule(context: Context, courseName: String, roomName: String, period: String, delayMillis: Long, scheduleId: Long) {
            val data = workDataOf(
                "course_name" to courseName, "room_name" to roomName,
                "period" to period, "schedule_id" to scheduleId
            )
            val request = OneTimeWorkRequestBuilder<CourseReminderWorker>()
                .setInputData(data)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            // scheduleId 唯一，避免同名同节不同天的提醒互相覆盖
            val uniqueName = "$UNIQUE_PREFIX$scheduleId"
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWork()
        }
    }
}