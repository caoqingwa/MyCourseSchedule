package com.example.courseschedule.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.courseschedule.data.db.dao.ExamDao
import com.example.courseschedule.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ExamReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val examDao: ExamDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val examName = inputData.getString("exam_name") ?: return Result.failure()
        val hoursUntilExam = inputData.getInt("hours_until_exam", 0)
        val examId = inputData.getLong("exam_id", -1L)

        if (hoursUntilExam <= 0) {
            if (examId >= 0) {
                examDao.getById(examId)?.let { exam ->
                    if (exam.examDate < System.currentTimeMillis()) {
                        examDao.delete(exam)
                    }
                }
            }
            return Result.success()
        }

        NotificationHelper.showExamReminder(applicationContext, examName, hoursUntilExam)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, examName: String, examMillis: Long, reminderHours: Int, examId: Long = -1L) {
            val now = System.currentTimeMillis()
            val delayMillis = (examMillis - now) - reminderHours.toLong() * 3600_000L
            if (delayMillis <= 0) return
            val hoursUntilExam = ((examMillis - now) / 3600_000L).toInt().coerceAtLeast(0)
            val data = workDataOf(
                "exam_name" to examName,
                "hours_until_exam" to hoursUntilExam,
                "exam_id" to examId
            )
            val request = OneTimeWorkRequestBuilder<ExamReminderWorker>()
                .setInputData(data)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "exam_reminder_$examId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
