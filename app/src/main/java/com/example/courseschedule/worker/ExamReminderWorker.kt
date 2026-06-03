package com.example.courseschedule.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.courseschedule.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ExamReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val examName = inputData.getString("exam_name") ?: return Result.failure()
        val daysLeft = inputData.getInt("days_left", 0)
        NotificationHelper.showExamReminder(applicationContext, examName, daysLeft)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, examName: String, daysLeft: Long, reminderDays: Int) {
            val delay = (daysLeft - reminderDays).coerceAtLeast(0)
            val data = workDataOf("exam_name" to examName, "days_left" to daysLeft.toInt())
            val request = OneTimeWorkRequestBuilder<ExamReminderWorker>()
                .setInputData(data)
                .setInitialDelay(delay, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}