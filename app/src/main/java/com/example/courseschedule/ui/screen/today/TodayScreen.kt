package com.example.courseschedule.ui.screen.today

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.courseschedule.ui.component.CourseCard
import com.example.courseschedule.ui.component.CourseScheduleTopBar
import com.example.courseschedule.ui.component.SemesterSetupDialog
import com.example.courseschedule.util.DateUtils
import com.example.courseschedule.util.NotificationPrefs
import com.example.courseschedule.worker.CourseReminderWorker
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onCourseClick: (Long) -> Unit,
    viewModel: TodayViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val todayStr = remember {
        SimpleDateFormat("yyyy\u5e74M\u6708d\u65e5 \u00b7 EEEE", Locale.CHINESE).format(Date())
    }
    var showSemesterDialog by remember { mutableStateOf(false) }

    val scheduledCourses = remember { mutableSetOf<Long>() }
    val notificationsEnabled by NotificationPrefs.enabled.collectAsStateWithLifecycle()
    LaunchedEffect(state.upcomingCourses, state.semester, notificationsEnabled) {
        if (!notificationsEnabled) {
            scheduledCourses.clear()
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        val periodTimes = state.semester?.getPeriodTimes().orEmpty()
        state.upcomingCourses.forEach { cws ->
            val scheduleId = cws.schedule.id
            if (scheduledCourses.add(scheduleId)) {
                val startTime = periodTimes.getOrNull(cws.schedule.startPeriod - 1)?.start
                    ?.split(":")?.let { (h, m) ->
                        Calendar.getInstance().apply {
                            timeInMillis = now
                            set(Calendar.HOUR_OF_DAY, h.toIntOrNull() ?: 0)
                            set(Calendar.MINUTE, m.toIntOrNull() ?: 0)
                            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    } ?: return@forEach
                val delayMillis = startTime - 5 * 60_000L - now
                if (delayMillis > 0) {
                    val roomName = cws.roomName ?: ""
                    val periodStr = "\u7b2c" + cws.schedule.startPeriod + "-" + cws.schedule.endPeriod + "\u8282"
                    CourseReminderWorker.schedule(viewModel.context, cws.course.name, roomName, periodStr, delayMillis, scheduleId)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val currentWeek = state.semester?.let { sem ->
            DateUtils.getWeekNumber(System.currentTimeMillis(), sem.startDate)
        } ?: 1

        CourseScheduleTopBar(
            selectedWeek = currentWeek,
            currentWeek = currentWeek,
            onSettingsClick = { showSemesterDialog = true }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(todayStr, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                Text(
                    "\u4eca\u5929",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        if (state.isEmpty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\ud83d\udcda", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "\u4eca\u5929\u6ca1\u6709\u8bfe\u7a0b\uff0c\u597d\u597d\u4f11\u606f\u5427",
                        fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "\u53bb\u6d3b\u52a8\u6d3b\u52a8\u5427 \ud83d\ude0a",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            val nextInfoList = remember(state.upcomingCourses) {
                state.upcomingCourses.mapIndexed { idx, item ->
                    if (idx < state.upcomingCourses.lastIndex) {
                        val next = state.upcomingCourses[idx + 1]
                        "\u4e0b\u4e00\u8282\uff1a" + next.course.name + " " + next.schedule.startPeriod + "-" + next.schedule.endPeriod + "\u8282"
                    } else null
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item(key = "header") {
                    Text(
                        "\u4eca\u65e5\u5269\u4f59 " + state.totalRemaining + " \u8282\u8bfe",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.currentCourse?.let { cws ->
                    item(key = "current") {
                        val nxt = remember(state.upcomingCourses) {
                            state.upcomingCourses.firstOrNull()?.let {
                                "\u4e0b\u4e00\u8282\uff1a" + it.course.name + " " + it.schedule.startPeriod + "-" + it.schedule.endPeriod + "\u8282"
                            }
                        }
                        CourseCard(cws, isCurrent = true, nextInfo = nxt, onClick = { onCourseClick(cws.course.id) })
                    }
                }
                itemsIndexed(
                    items = state.upcomingCourses,
                    key = { _, item -> item.schedule.id }
                ) { idx, item ->
                    CourseCard(item, isCurrent = false, nextInfo = nextInfoList[idx], onClick = { onCourseClick(item.course.id) })
                }
            }
        }
    }

    if (showSemesterDialog) {
        SemesterSetupDialog(
            semester = state.semester,
            savedPresets = state.presets.filter { it.id != state.semester?.id },
            maxScheduledPeriod = state.maxScheduledPeriod,
            hasWeekendCourses = state.hasWeekendCourses,
            onDismiss = { showSemesterDialog = false },
            onConfirm = { name, startDate, totalWeeks, periodCount, weekDays, periodTimesJson ->
                viewModel.saveSemester(name, startDate, totalWeeks, periodCount, weekDays, periodTimesJson)
                showSemesterDialog = false
            },
            onLoadPreset = { preset ->
                viewModel.saveSemester(preset.name, preset.startDate, preset.totalWeeks, preset.periodCount, preset.weekDays, preset.periodTimesJson)
            },
            onDeletePreset = { viewModel.deletePreset(it) }
        )
    }
}