package com.example.courseschedule.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.courseschedule.data.db.dao.ExamDao
import com.example.courseschedule.util.NotificationHelper
import com.example.courseschedule.util.NotificationPrefs
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
        val reminderHours = inputData.getInt("reminder_hours", -1)
        val exam = examDao.getById(examId) ?: return Result.success()
        if (exam.examDate != examMillis || (reminderHours >= 0 && exam.reminderHours != reminderHours)) {
            return Result.success()
        }

        if (examMillis <= 0L) return Result.failure()

        val now = System.currentTimeMillis()
        val hoursLeft = ((examMillis - now) / 3_600_000L).toInt().coerceAtLeast(0)

        if (examMillis <= now) return Result.success()

        if (!NotificationPrefs.isEnabled()) {
            // 用户已关闭提醒：即便有历史排定任务也不推送
            return Result.success()
        }

        val prefs = applicationContext.getSharedPreferences(REMINDER_TAG, Context.MODE_PRIVATE)
        val key = examId.toString()
        val fingerprint = "${exam.examDate}:${exam.reminderHours}"
        if (prefs.getString(key, null) != fingerprint &&
            NotificationHelper.showExamReminder(applicationContext, exam.notes ?: examName, hoursLeft, examId)
        ) {
            prefs.edit().putString(key, fingerprint).apply()
        }
        return Result.success()
    }

    companion object {
        private const val REMINDER_TAG = "exam_reminders"

        fun schedule(
            context: Context,
            examName: String,
            examMillis: Long,
            reminderHours: Int,
            examId: Long,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
        ) {
            val now = System.currentTimeMillis()
            val triggerMillis = examMillis - reminderHours.toLong() * 3_600_000L
            val delayMillis = (triggerMillis - now).coerceAtLeast(0L)

            val data = workDataOf(
                "exam_name" to examName,
                "exam_millis" to examMillis,
                "exam_id" to examId,
                "reminder_hours" to reminderHours
            )
            val request = OneTimeWorkRequestBuilder<ExamReminderWorker>()
                .setInputData(data)
                .addTag(REMINDER_TAG)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "exam_reminder_$examId",
                policy,
                request
            )
        }

        fun cancel(context: Context, examId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("exam_reminder_$examId")
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(REMINDER_TAG)
        }

        suspend fun rescheduleAll(
            context: Context,
            examDao: ExamDao,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP
        ) {
            if (!NotificationPrefs.isEnabled() || !NotificationHelper.canShowExamReminders(context)) return
            val now = System.currentTimeMillis()
            val exams = examDao.getAllPending(now)
            val prefs = context.getSharedPreferences(REMINDER_TAG, Context.MODE_PRIVATE)
            for (exam in exams) {
                if (prefs.getString(exam.id.toString(), null) == "${exam.examDate}:${exam.reminderHours}") continue
                val courseName = exam.notes ?: "\u8003\u8bd5"
                schedule(context, courseName, exam.examDate, exam.reminderHours, exam.id, policy)
            }
        }
    }
}
