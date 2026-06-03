const fs = require("fs");
const w = (p, c) => { fs.writeFileSync(p, c, "utf8"); console.log("OK: " + p.split("/").pop()); };
const B = "F:/Projects/CourseSchedule/app/src/main/java/com/example/courseschedule";

// CourseDetailSheet
w(B+"/ui/screen/detail/CourseDetailSheet.kt",
`package com.example.courseschedule.ui.screen.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    course: Course,
    schedules: List<Schedule>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(course.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 16.dp))

            DetailRow(icon = "\ud83d\udccd", label = "\u6559\u5ba4", value = "\u5f85\u5b9e\u73b0")
            DetailRow(icon = "\ud83d\udc68\u200d\ud83c\udfeb", label = "\u6559\u5e08", value = course.teacher)

            val weekTypes = mapOf(0 to "\u5168\u5468", 1 to "\u5355\u5468", 2 to "\u53cc\u5468")
            val weekInfo = schedules.firstOrNull()?.let { "\u7b2c" + it.startWeek + "-" + it.endWeek + "\u5468 " + (weekTypes[it.weekType] ?: "") } ?: "\u5f85\u914d\u7f6e"
            DetailRow(icon = "\ud83d\udcc5", label = "\u8bfe\u7a0b\u5468\u6570", value = weekInfo)

            val scheduleInfo = schedules.joinToString(", ") { "\u5468" + it.dayOfWeek + " " + it.startPeriod + "-" + it.endPeriod + "\u8282" }
            DetailRow(icon = "\ud83d\udd50", label = "\u65f6\u95f4\u5b89\u6392", value = scheduleInfo.ifEmpty { "\u5f85\u914d\u7f6e" })

            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("\u5173\u95ed") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onEdit) { Text("\u7f16\u8f91") }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(icon: String, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 14.sp, modifier = Modifier.width(32.dp))
        Column { Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) }
    }
}
`);

// MainActivity
w(B+"/MainActivity.kt",
`package com.example.courseschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.courseschedule.ui.navigation.Screen
import com.example.courseschedule.ui.navigation.bottomNavItems
import com.example.courseschedule.ui.component.BottomNavBar
import com.example.courseschedule.ui.screen.today.TodayScreen
import com.example.courseschedule.ui.screen.week.WeekScreen
import com.example.courseschedule.ui.screen.calendar.CalendarScreen
import com.example.courseschedule.ui.theme.CourseScheduleTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CourseScheduleTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            BottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    navController.navigate(screen.route) {
                        popUpTo(Screen.Today.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                screens = bottomNavItems
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Today.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Today.route) {
                TodayScreen(onCourseClick = { /* navigate to detail */ })
            }
            composable(Screen.Week.route) {
                WeekScreen(onCourseClick = { /* navigate to detail */ })
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    onDayClick = { navController.navigate(Screen.Today.route) },
                    onNavigateToToday = { navController.navigate(Screen.Today.route) }
                )
            }
        }
    }
}
`);

// CourseReminderWorker
w(B+"/worker/CourseReminderWorker.kt",
`package com.example.courseschedule.worker

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
`);

// ExamReminderWorker
w(B+"/worker/ExamReminderWorker.kt",
`package com.example.courseschedule.worker

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
                .setDelay(delay, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
`);

console.log("DetailSheet + MainActivity + Workers done");