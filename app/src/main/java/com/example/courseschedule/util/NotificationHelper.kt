package com.example.courseschedule.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.courseschedule.MainActivity

object NotificationHelper {
    fun showCourseReminder(context: Context, courseName: String, roomName: String, period: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, "course_reminder")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(courseName)
            .setContentText(period + " \u00b7 " + roomName + " \u5373\u5c06\u5f00\u59cb")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(courseName.hashCode(), notification)
        } catch (_: SecurityException) {}
    }

    fun showExamReminder(context: Context, examName: String, hoursUntilExam: Int) {
        val timeText = when {
            hoursUntilExam >= 48 -> (hoursUntilExam / 24).toString() + " \u5929"
            hoursUntilExam >= 24 -> "1 \u5929" + (hoursUntilExam % 24).toString() + "\u5c0f\u65f6"
            else -> hoursUntilExam.toString() + " \u5c0f\u65f6"
        }
        val notification = NotificationCompat.Builder(context, "exam_reminder")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("\u8003\u8bd5\u63d0\u9192")
            .setContentText(examName + " \u8fd8\u6709 " + timeText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(examName.hashCode() + 10000, notification)
        } catch (_: SecurityException) {}
    }
}