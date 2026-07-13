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
        val examMillis = inputData.getLong("exam_millis", 0L)
        val examId = inputData.getLong("exam_id", -1L)

        if (examMillis <= 0L) return Result.failure()

        val now = System.currentTimeMillis()
        val hoursLeft = ((examMillis - now) / 3_600_000L).toInt().coerceAtLeast(0)

        if (examMillis < now) {
            // 考试已过期，清理
            if (examId >= 0) {
                examDao.getById(examId)?.let { exam ->
                    if (exam.examDate < now) examDao.delete(exam)
                }
            }
            return Result.success()
        }

        if (!NotificationHelper.hasNotificationPermission(applicationContext)) {
            return Result.success()
        }

        NotificationHelper.showExamReminder(applicationContext, examName, hoursLeft)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, examName: String, examMillis: Long, reminderHours: Int, examId: Long = -1L) {
            val now = System.currentTimeMillis()
            val triggerMillis = examMillis - reminderHours.toLong() * 3_600_000L
            val delayMillis = (triggerMillis - now).coerceAtLeast(0L)

            val data = workDataOf(
                "exam_name" to examName,
                "exam_millis" to examMillis,
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

        suspend fun rescheduleAll(context: Context, examDao: ExamDao) {
            val exams = examDao.getAllPending(System.currentTimeMillis())
            for (exam in exams) {
                val courseName = exam.notes ?: "\u8003\u8bd5"
                schedule(context, courseName, exam.examDate, exam.reminderHours, exam.id)
            }
        }
    }
}
