package com.example.courseschedule.ui.screen.today

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import com.example.courseschedule.ui.component.SemesterSetupDialog
import com.example.courseschedule.util.DateUtils
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

    LaunchedEffect(state.upcomingCourses) {
        state.upcomingCourses.forEach { cws ->
            val roomName = cws.roomName ?: ""
            val periodStr = "\u7b2c" + cws.schedule.startPeriod + "-" + cws.schedule.endPeriod + "\u8282"
            CourseReminderWorker.schedule(viewModel.context, cws.course.name, roomName, periodStr, 5)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\u8bfe\u7a0b\u8868", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(8.dp))
                    state.semester?.let { sem ->
                        val wk = remember(state) { DateUtils.getWeekNumber(System.currentTimeMillis(), sem.startDate) }
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(
                                "\u7b2c${wk}\u5468",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            actions = {
                IconButton(onClick = { showSemesterDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "\u5b66\u671f\u8bbe\u7f6e")
                }
            }
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
                        val nxt = remember(state) {
                            state.upcomingCourses.firstOrNull()?.let {
                                "\u4e0b\u4e00\u8282\uff1a" + it.course.name + " " + it.schedule.startPeriod + "-" + it.schedule.endPeriod + "\u8282"
                            }
                        }
                        CourseCard(cws, isCurrent = true, nextInfo = nxt, onClick = { onCourseClick(cws.course.id) })
                    }
                }
                itemsIndexed(
                    items = state.upcomingCourses,
                    key = { _, item -> item.course.id * 1000 + item.schedule.startPeriod }
                ) { idx, item ->
                    val nxt = if (idx < state.upcomingCourses.lastIndex) {
                        val next = state.upcomingCourses[idx + 1]
                        "\u4e0b\u4e00\u8282\uff1a" + next.course.name + " " + next.schedule.startPeriod + "-" + next.schedule.endPeriod + "\u8282"
                    } else null
                    CourseCard(item, isCurrent = false, nextInfo = nxt, onClick = { onCourseClick(item.course.id) })
                }
            }
        }
    }

    if (showSemesterDialog) {
        SemesterSetupDialog(
            semester = state.semester,
            savedPresets = state.presets.filter { it.id != state.semester?.id },
            onDismiss = { showSemesterDialog = false },
            onConfirm = { name, startDate, totalWeeks, periodCount, periodTimesJson ->
                viewModel.saveSemester(name, startDate, totalWeeks, periodCount, periodTimesJson)
                showSemesterDialog = false
            },
            onLoadPreset = { },
            onDeletePreset = { viewModel.deletePreset(it) }
        )
    }
}