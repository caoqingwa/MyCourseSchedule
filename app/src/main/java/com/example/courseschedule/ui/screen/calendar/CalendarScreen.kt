package com.example.courseschedule.ui.screen.calendar

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.courseschedule.data.db.entity.Exam
import com.example.courseschedule.ui.component.AddExamDialog
import com.example.courseschedule.ui.component.CalendarPicker
import com.example.courseschedule.util.DateUtils
import com.example.courseschedule.util.NotificationHelper
import com.example.courseschedule.util.scheduleOccurrences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    onDayClick: (dayMillis: Long, weekNumber: Int) -> Unit,
    onOpenSettings: () -> Unit = {},
    pendingExamId: Long? = null,
    onPendingExamOpened: () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    var selectedDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val changedDays = remember(state.scheduleChanges) {
        buildSet {
            state.scheduleChanges.forEach {
                add(it.originalDay)
                it.targetDay?.let { day -> add(day) }
            }
        }
    }
    val cal = remember { Calendar.getInstance() }
    // rememberSaveable：Tab 被 Pager 销毁重建后仍保留当前浏览月份
    var displayMonth by rememberSaveable { mutableIntStateOf(cal.get(Calendar.MONTH)) }
    var displayYear by rememberSaveable { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    val semester = state.semester
    val today = LocalDate.now(zone)
    val firstDay = semester?.let {
        val start = Instant.ofEpochMilli(it.startDate).atZone(zone).toLocalDate()
        start.minusDays((start.dayOfWeek.value - 1).toLong())
    }
    val lastDay = firstDay?.plusWeeks((semester?.totalWeeks ?: 1).toLong())?.minusDays(1)
    val anchor = if (firstDay != null && lastDay != null && today !in firstDay..lastDay) firstDay else today
    LaunchedEffect(semester?.id, firstDay, lastDay) {
        if (firstDay == null || lastDay == null) return@LaunchedEffect
        val displayed = YearMonth.of(displayYear, displayMonth + 1)
        if (displayed !in YearMonth.from(firstDay)..YearMonth.from(lastDay)) {
            displayYear = anchor.year
            displayMonth = anchor.monthValue - 1
        }
        val selected = selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        if (selected == null || selected !in firstDay..lastDay) {
            selectedDateMillis = if (anchor == today) null else anchor.atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }

    // 倒计时按分钟自己走，否则停在这一页时「还剩 X 小时」会一直停在打开时的数字
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val summaryToday = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000L)
        }
    }
    var showPastExams by rememberSaveable(state.semester?.id) { mutableStateOf(false) }
    val (upcomingExams, pastExams) = remember(state.exams, nowMillis) {
        state.exams.partition { it.examDate > nowMillis }
    }
    val displayedExams = remember(upcomingExams, pastExams, showPastExams) {
        if (showPastExams) upcomingExams + pastExams.asReversed() else upcomingExams
    }

    var showAddExamDialog by remember { mutableStateOf(false) }
    var showEditExamDialog by remember { mutableStateOf(false) }
    var examDialogDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var examToEdit by remember { mutableStateOf<Exam?>(null) }

    var showExamDetailSheet by remember { mutableStateOf(false) }
    var examToShowDetail by remember { mutableStateOf<Exam?>(null) }
    var examUnavailable by remember { mutableStateOf(false) }
    LaunchedEffect(pendingExamId) {
        val id = pendingExamId ?: return@LaunchedEffect
        val exam = viewModel.findExam(id)
        if (exam == null) {
            examUnavailable = true
        } else {
            examToShowDetail = exam
            showExamDetailSheet = true
        }
        onPendingExamOpened()
    }
    if (examUnavailable) {
        AlertDialog(
            onDismissRequest = { examUnavailable = false },
            text = { Text("该考试记录已不存在") },
            confirmButton = { TextButton(onClick = { examUnavailable = false }) { Text("确定") } }
        )
    }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // 考试列表按课程 id 取名称：建一次 map，避免每个 item 里 O(课程数) 的 find
    val courseMap = remember(state.courses) { state.courses.associateBy { it.id } }
    val summaryDate = selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?: Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val summaryCourses = remember(summaryDate, state.semester, state.schedules, state.scheduleChanges, zone) {
        state.semester?.let { semester ->
            val start = Instant.ofEpochMilli(semester.startDate).atZone(zone).toLocalDate()
            scheduleOccurrences(summaryDate, start, semester.totalWeeks, state.schedules, state.scheduleChanges)
        }.orEmpty()
    }
    val summaryExams = remember(summaryDate, state.exams, zone) {
        val start = summaryDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = summaryDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        state.exams.filter { it.examDate in start until end }
    }
    val summaryChanges = remember(summaryDate, state.scheduleChanges) {
        state.scheduleChanges.filter { it.originalDay == summaryDate.toEpochDay() }
    }
    val schedulesById = remember(state.schedules) { state.schedules.associateBy { it.id } }

    var systemNotificationsEnabled by remember {
        mutableStateOf(NotificationHelper.canShowExamReminders(context))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val available = NotificationHelper.canShowExamReminders(context)
        if (available && !systemNotificationsEnabled && state.notificationsEnabled) {
            viewModel.setNotificationsEnabled(true)
        }
        systemNotificationsEnabled = available
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        systemNotificationsEnabled = NotificationHelper.canShowExamReminders(context)
        if (granted) {
            viewModel.setNotificationsEnabled(true)
        }
    }

    // 提醒开关为开但缺少通知权限（Android 13+）时主动申请，否则 worker 会静默丢弃提醒
    LaunchedEffect(state.notificationsEnabled) {
        if (state.notificationsEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.hasNotificationPermission(context)
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Add exam dialog
    if (showAddExamDialog) {
        AddExamDialog(
            courses = state.courses,
            initialDateMillis = examDialogDateMillis,
            onDismiss = { showAddExamDialog = false },
            onConfirm = { courseId, examDate, reminderHours, notes ->
                scope.launch {
                    viewModel.addExam(courseId, examDate, reminderHours, notes)
                }
                showAddExamDialog = false
            }
        )
    }

    // Edit exam dialog
    if (showEditExamDialog && examToEdit != null) {
        AddExamDialog(
            courses = state.courses,
            initialDateMillis = examToEdit!!.examDate,
            editingExam = examToEdit,
            onDismiss = { showEditExamDialog = false },
            onConfirm = { courseId, examDate, reminderHours, notes ->
                viewModel.updateExam(
                    examToEdit!!.copy(
                        courseId = courseId,
                        examDate = examDate,
                        reminderHours = reminderHours,
                        notes = notes
                    )
                )
                showEditExamDialog = false
            },
            onDelete = {
                viewModel.deleteExam(examToEdit!!)
                showEditExamDialog = false
            }
        )
    }

    // Exam detail dialog
    if (showExamDetailSheet && examToShowDetail != null) {
        val exam = examToShowDetail!!
        val courseName = state.courses.find { it.id == exam.courseId }?.name
        val hoursLeft = ((exam.examDate - nowMillis) / 3600_000L).toInt().coerceAtLeast(0)
        val daysLeft = (hoursLeft / 24)
        val examCal = Calendar.getInstance().apply { timeInMillis = exam.examDate }
        val dayOfWeekNames = remember { listOf("\u5468\u65e5", "\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94", "\u5468\u516d") }
        val dateFmt = remember { SimpleDateFormat("yyyy\u5e74M\u6708d\u65e5", Locale.getDefault()) }
        val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        var showDeleteConfirm by remember { mutableStateOf(false) }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("\u5220\u9664\u8003\u8bd5") },
                text = { Text("\u786e\u5b9a\u5220\u9664\u300c${exam.notes ?: courseName ?: "\u8003\u8bd5"}\u300d\uff1f") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteExam(exam)
                        showDeleteConfirm = false
                        showExamDetailSheet = false
                    }) { Text("\u5220\u9664", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("\u53d6\u6d88") }
                }
            )
        }

        AlertDialog(
            onDismissRequest = { showExamDetailSheet = false },
            title = {
                Text("\u8003\u8bd5\u8be6\u60c5", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow("\u8003\u8bd5\u540d\u79f0", exam.notes ?: courseName ?: "\u8003\u8bd5")
                    if (courseName != null) {
                        DetailRow("\u5173\u8054\u8bfe\u7a0b", courseName)
                    }
                    DetailRow("\u8003\u8bd5\u65e5\u671f", dateFmt.format(Date(exam.examDate)))
                    DetailRow("\u8003\u8bd5\u65f6\u95f4", timeFmt.format(Date(exam.examDate)))
                    DetailRow("\u661f\u671f", dayOfWeekNames[examCal.get(Calendar.DAY_OF_WEEK) - 1])
                    val reminderText = if (exam.reminderHours >= 24) {
                        "\u8003\u8bd5\u524d " + (exam.reminderHours / 24) + " \u5929"
                    } else {
                        "\u8003\u8bd5\u524d " + exam.reminderHours + " \u5c0f\u65f6"
                    }
                    DetailRow("\u63d0\u524d\u63d0\u9192", reminderText)
                    val remainText = if (daysLeft > 0) {
                        daysLeft.toString() + " \u5929" + (hoursLeft % 24) + "\u5c0f\u65f6"
                    } else {
                        hoursLeft.toString() + " \u5c0f\u65f6"
                    }
                    DetailRow("考试状态", if (exam.examDate <= nowMillis) "已到开考时间" else "还有 $remainText")
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { showExamDetailSheet = false }) { Text("\u5173\u95ed") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        showExamDetailSheet = false
                        examToEdit = exam
                        showEditExamDialog = true
                    }) { Text("\u7f16\u8f91") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("\u5220\u9664", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {}
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text("\u65e5\u5386", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    // 回到今天：翻到别的月份后不用一周周划回来
                    IconButton(onClick = {
                        displayMonth = anchor.monthValue - 1
                        displayYear = anchor.year
                        selectedDateMillis = if (anchor == today) null else anchor.atStartOfDay(zone).toInstant().toEpochMilli()
                    }) {
                        Icon(Icons.Default.Today, contentDescription = if (anchor == today) "回到今天" else "回到学期起始日期")
                    }
                    // Notification toggle
                    IconButton(onClick = {
                        if (state.notificationsEnabled && !systemNotificationsEnabled) {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            })
                        } else if (state.notificationsEnabled) {
                            viewModel.setNotificationsEnabled(false)
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !NotificationHelper.hasNotificationPermission(context)
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setNotificationsEnabled(true)
                            }
                        }
                    }) {
                        Icon(
                            if (state.notificationsEnabled && systemNotificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                            contentDescription = when {
                                !state.notificationsEnabled -> "开启提醒"
                                !systemNotificationsEnabled -> "系统通知已关闭，打开通知设置"
                                else -> "关闭提醒"
                            },
                            tint = if (state.notificationsEnabled && systemNotificationsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 全局设置
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "\u8bbe\u7f6e")
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 84.dp)
            ) {
                item {
                    state.semester?.let { sem ->
                        CalendarPicker(
                            currentMonth = displayMonth,
                            currentYear = displayYear,
                            todayMillis = summaryToday.atStartOfDay(zone).toInstant().toEpochMilli(),
                            semesterStartMillis = sem.startDate,
                            totalWeeks = sem.totalWeeks,
                            changedDays = changedDays,
                            onDayClick = { dayMillis ->
                                selectedDateMillis = dayMillis
                                val weekNum = DateUtils.getWeekNumber(dayMillis, sem.startDate)
                                onDayClick(dayMillis, weekNum)
                            },
                            onMonthChange = { month, year ->
                                displayMonth = month
                                displayYear = year
                            },
                            onDayLongPress = { dayMillis ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                examDialogDateMillis = dayMillis
                                showAddExamDialog = true
                            }
                        )
                        if (changedDays.isNotEmpty()) {
                            Text("日期下方圆点表示有课程调整", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("${summaryDate.monthValue}月${summaryDate.dayOfMonth}日安排", style = MaterialTheme.typography.titleSmall)
                        if (summaryCourses.isEmpty() && summaryExams.isEmpty() && summaryChanges.isEmpty()) {
                            Text("当天没有安排", style = MaterialTheme.typography.bodySmall)
                        }
                        for (occurrence in summaryCourses) {
                            val schedule = occurrence.schedule
                            val name = courseMap[schedule.courseId]?.name ?: "课程"
                            val room = if (occurrence.change != null) occurrence.change.roomName
                                else schedule.roomId?.let { state.rooms[it] }
                            val prefix = if (occurrence.change != null) "调课 · " else ""
                            Text("$prefix 第 ${schedule.startPeriod}–${schedule.endPeriod} 节 · $name${room?.let { " · $it" }.orEmpty()}")
                        }
                        for (exam in summaryExams) {
                            val time = Instant.ofEpochMilli(exam.examDate).atZone(zone)
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                            TextButton(onClick = { examToShowDetail = exam; showExamDetailSheet = true }) {
                                Text("$time · ${exam.notes ?: courseMap[exam.courseId]?.name ?: "考试"}")
                            }
                        }
                        for (change in summaryChanges) {
                            if (change.keepOriginal || change.targetDay == change.originalDay) continue
                            val name = schedulesById[change.scheduleId]?.let { courseMap[it.courseId]?.name } ?: "课程"
                            val status = change.targetDay?.let { "改至 ${LocalDate.ofEpochDay(it)}" } ?: "停课"
                            Text("$name · $status")
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "即将考试",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.notificationsEnabled && systemNotificationsEnabled) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    " \u63d0\u9192\u5df2\u5f00\u542f ",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    if (state.notificationsEnabled) " 系统通知已关闭 " else " 提醒已关闭 ",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (pastExams.isNotEmpty()) {
                        TextButton(onClick = { showPastExams = !showPastExams }) {
                            val action = if (showPastExams) "收起" else "查看"
                            Text("${action}历史考试（${pastExams.size}）")
                        }
                    }
                }

                if (upcomingExams.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Text(
                                "近期没有考试",
                                modifier = Modifier.padding(14.dp),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                    items(displayedExams, key = { it.id }) { exam ->
                        val past = exam.examDate <= nowMillis
                        val hoursLeftVal = ((exam.examDate - nowMillis) / 3600_000L).toInt().coerceAtLeast(0)
                        val daysLeftVal = hoursLeftVal / 24
                        val badgeColor = when {
                            past -> MaterialTheme.colorScheme.surfaceContainerHighest
                            daysLeftVal <= 3 -> MaterialTheme.colorScheme.error
                            daysLeftVal <= 7 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val badgeText = when {
                            past -> "已开考"
                            daysLeftVal > 0 -> "还有 ${daysLeftVal}天"
                            hoursLeftVal > 0 -> "还有 ${hoursLeftVal}小时"
                            else -> "不足 1 小时"
                        }
                        val courseName = courseMap[exam.courseId]?.name
                        val dateFmt = remember { SimpleDateFormat("M\u6708d\u65e5", Locale.getDefault()) }
                        val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                        val dayOfWeekNames = remember { listOf("\u5468\u65e5", "\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94", "\u5468\u516d") }
                        val examCal = remember(exam.examDate) { Calendar.getInstance().apply { timeInMillis = exam.examDate } }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                .combinedClickable(
                                    onClick = {
                                        examToShowDetail = exam
                                        showExamDetailSheet = true
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        examToEdit = exam
                                        showEditExamDialog = true
                                    }
                                )
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        exam.notes ?: courseName ?: "\u8003\u8bd5",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (courseName != null && exam.notes != null) {
                                        Text(courseName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        dateFmt.format(Date(exam.examDate)) +
                                                " " + timeFmt.format(Date(exam.examDate)) +
                                                " \u00b7 " + dayOfWeekNames[examCal.get(Calendar.DAY_OF_WEEK) - 1],
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(color = badgeColor, shape = RoundedCornerShape(10.dp)) {
                                    Text(
                                        badgeText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
            }
        }

        // FAB for adding exam
        FloatingActionButton(
            onClick = {
                examDialogDateMillis = System.currentTimeMillis()
                showAddExamDialog = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(Icons.Default.Add, contentDescription = "\u6dfb\u52a0\u8003\u8bd5")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}
