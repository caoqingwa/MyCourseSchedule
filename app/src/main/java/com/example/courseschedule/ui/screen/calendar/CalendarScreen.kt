package com.example.courseschedule.ui.screen.calendar

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.courseschedule.data.db.entity.Exam
import com.example.courseschedule.ui.component.AddExamDialog
import com.example.courseschedule.ui.component.CalendarPicker
import com.example.courseschedule.util.DateUtils
import com.example.courseschedule.worker.ExamReminderWorker
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    onDayClick: (dayMillis: Long, weekNumber: Int) -> Unit,
    onNavigateToToday: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cal = remember { Calendar.getInstance() }
    var displayMonth by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) }
    var displayYear by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }

    var showAddExamDialog by remember { mutableStateOf(false) }
    var showEditExamDialog by remember { mutableStateOf(false) }
    var examDialogDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var examToEdit by remember { mutableStateOf<Exam?>(null) }

    var showExamDetailSheet by remember { mutableStateOf(false) }
    var examToShowDetail by remember { mutableStateOf<Exam?>(null) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Add exam dialog
    if (showAddExamDialog) {
        AddExamDialog(
            courses = state.courses,
            initialDateMillis = examDialogDateMillis,
            onDismiss = { showAddExamDialog = false },
            onConfirm = { courseId, examDate, reminderHours, notes ->
                scope.launch {
                    val examId = viewModel.addExam(courseId, examDate, reminderHours, notes)
                    ExamReminderWorker.schedule(context, notes ?: "\u8003\u8bd5", examDate, reminderHours, examId)
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
        val hoursLeft = ((exam.examDate - System.currentTimeMillis()) / 3600_000L).toInt().coerceAtLeast(0)
        val daysLeft = (hoursLeft / 24)
        val examCal = Calendar.getInstance().apply { timeInMillis = exam.examDate }
        val dayOfWeekNames = remember { listOf("\u5468\u65e5", "\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e94", "\u5468\u56db", "\u5468\u4e94", "\u5468\u516d") }
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
                    DetailRow("\u5269\u4f59\u65f6\u95f4", remainText)
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
            TopAppBar(title = {
                Text("\u8bfe\u7a0b\u8868", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            })

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    state.semester?.let { sem ->
                        CalendarPicker(
                            currentMonth = displayMonth,
                            currentYear = displayYear,
                            todayMillis = state.todayMillis,
                            semesterStartMillis = sem.startDate,
                            currentWeek = state.currentWeek,
                            totalWeeks = sem.totalWeeks,
                            onDayClick = { dayMillis ->
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
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "\u8003\u8bd5\u5b89\u6392",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                if (state.exams.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Text(
                                "\u6682\u65e0\u8003\u8bd5\u5b89\u6392",
                                modifier = Modifier.padding(14.dp),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(state.exams, key = { it.id }) { exam ->
                        val hoursLeftVal = ((exam.examDate - state.todayMillis) / 3600_000L).toInt().coerceAtLeast(0)
                        val daysLeftVal = hoursLeftVal / 24
                        val badgeColor = when {
                            daysLeftVal <= 3 -> MaterialTheme.colorScheme.error
                            daysLeftVal <= 7 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val badgeText = if (daysLeftVal > 0) {
                            " \u8fd8\u6709 " + daysLeftVal + "\u5929 "
                        } else {
                            " \u8fd8\u6709 " + hoursLeftVal + "\u5c0f\u65f6 "
                        }
                        val courseName = state.courses.find { it.id == exam.courseId }?.name
                        val dateFmt = remember { SimpleDateFormat("M\u6708d\u65e5", Locale.getDefault()) }
                        val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                        val dayOfWeekNames = remember { listOf("\u5468\u65e5", "\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e94", "\u5468\u56db", "\u5468\u4e94", "\u5468\u516d") }
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
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
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