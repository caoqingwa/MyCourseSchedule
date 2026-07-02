package com.example.courseschedule.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.courseschedule.MainActivity

object NotificationHelper {
    private const val CHANNEL_COURSE = "course_reminder"
    private const val CHANNEL_EXAM = "exam_reminder"

    private fun baseIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun showCourseReminder(context: Context, courseName: String, roomName: String, period: String) {
        if (!hasNotificationPermission(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_COURSE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(courseName)
            .setContentText(period + " \u00b7 " + roomName + " \u5373\u5c06\u5f00\u59cb")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(baseIntent(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(courseName.hashCode(), notification)
        } catch (_: SecurityException) {}
    }

    fun showExamReminder(context: Context, examName: String, hoursUntilExam: Int) {
        if (!hasNotificationPermission(context)) return
        val timeText = when {
            hoursUntilExam >= 48 -> (hoursUntilExam / 24).toString() + " \u5929"
            hoursUntilExam >= 24 -> "1 \u5929" + (hoursUntilExam % 24).toString() + "\u5c0f\u65f6"
            else -> hoursUntilExam.toString() + " \u5c0f\u65f6"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_EXAM)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("\u8003\u8bd5\u63d0\u9192")
            .setContentText(examName + " \u8fd8\u6709 " + timeText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(examName + " \u8fd8\u6709 " + timeText + "\uff0c\u8bf7\u505a\u597d\u51c6\u5907\uff01"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(baseIntent(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(examName.hashCode() + 10000, notification)
        } catch (_: SecurityException) {}
    }
}
