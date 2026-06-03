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
            .setContentText("$period · $roomName 即将开始")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(courseName.hashCode(), notification)
        } catch (_: SecurityException) {}
    }

    fun showExamReminder(context: Context, examName: String, daysLeft: Int) {
        val notification = NotificationCompat.Builder(context, "exam_reminder")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("考试提醒")
            .setContentText("$examName 还有 $daysLeft 天")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(examName.hashCode() + 10000, notification)
        } catch (_: SecurityException) {}
    }
}
