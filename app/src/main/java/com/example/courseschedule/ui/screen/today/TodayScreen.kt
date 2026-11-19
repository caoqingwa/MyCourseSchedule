package com.example.courseschedule.ui.screen.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.courseschedule.ui.component.CourseCard
import com.example.courseschedule.ui.component.CourseScheduleTopBar
import com.example.courseschedule.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onCourseClick: (courseId: Long, scheduleId: Long, originalDay: Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    onAddCourse: () -> Unit = {},
    onImportCourse: () -> Unit = {},
    onExamClick: (Long) -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCompleted by rememberSaveable(state.todayLabel) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val currentWeek = state.semester?.let { sem ->
                DateUtils.getWeekNumber(System.currentTimeMillis(), sem.startDate)
            } ?: 1

            CourseScheduleTopBar(
                selectedWeek = currentWeek,
                currentWeek = currentWeek,
                onSettingsClick = onOpenSettings
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 日期文案来自 ticker 驱动的 state，跨零点会自动更新
                Text(state.todayLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                    Text(
                        "\u4eca\u5929",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            if (state.isEmpty && state.exams.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when {
                            !state.hasSchedule -> {
                                Text("还没录入课表")
                                FilledTonalButton(onClick = onImportCourse) { Text("导入 Excel") }
                                TextButton(onClick = onAddCourse) { Text("手动排课") }
                            }
                            state.beforeSemesterStart -> {
                                Text("\ud83d\udcc5", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "\u5b66\u671f\u8fd8\u672a\u5f00\u59cb\uff0c\u653e\u677e\u4e00\u4e0b\u5427",
                                    fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            state.afterSemesterEnd -> {
                                Text("\ud83c\udf89", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "\u5b66\u671f\u5df2\u7ed3\u675f\uff0c\u5f00\u5f00\u5fc3\u5fc3\u8fc7\u5047\u671f",
                                    fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> {
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
                                Spacer(modifier = Modifier.height(16.dp))
                                FilledTonalButton(onClick = onAddCourse) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("\u53bb\u6dfb\u52a0\u8bfe\u7a0b", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                val nextInfoList = remember(state.upcomingCourses) {
                    state.upcomingCourses.mapIndexed { idx, _ ->
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
                            when {
                                state.isEmpty -> "今天没有课程"
                                state.totalRemaining == 0 -> "今天的课已结束"
                                state.currentCourses.isNotEmpty() -> "正在上课"
                                state.exams.isNotEmpty() -> "今日安排"
                                else -> "下一节课"
                            },
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(state.currentCourses, key = { it.occurrenceKey }) { cws ->
                            val nxt = remember(state.upcomingCourses) {
                                state.upcomingCourses.firstOrNull()?.let {
                                    "\u4e0b\u4e00\u8282\uff1a" + it.course.name + " " + it.schedule.startPeriod + "-" + it.schedule.endPeriod + "\u8282"
                                }
                            }
                            CourseCard(cws, isCurrent = true, nextInfo = nxt, semester = state.semester, onClick = { onCourseClick(cws.course.id, cws.schedule.id, cws.originalDay) })
                    }
                    var nextCourseIndex = 0
                    var nextExamIndex = 0
                    while (nextCourseIndex < state.upcomingCourses.size || nextExamIndex < state.exams.size) {
                        val course = state.upcomingCourses.getOrNull(nextCourseIndex)
                        val exam = state.exams.getOrNull(nextExamIndex)
                        if (course != null && (exam == null || course.startsAtMillis <= exam.exam.examDate)) {
                            val index = nextCourseIndex++
                            item(key = course.occurrenceKey) {
                                if (index == 0 && state.currentCourses.isEmpty() && state.exams.isNotEmpty()) {
                                    Text("下一节课", style = MaterialTheme.typography.labelMedium)
                                }
                                CourseCard(
                                    course, isCurrent = false, nextInfo = nextInfoList[index], semester = state.semester,
                                    onClick = { onCourseClick(course.course.id, course.schedule.id, course.originalDay) }
                                )
                            }
                        } else if (exam != null) {
                            nextExamIndex++
                            item(key = "exam_${exam.exam.id}") {
                                val time = remember(exam.exam.examDate) {
                                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(exam.exam.examDate))
                                }
                                Card(onClick = { onExamClick(exam.exam.id) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("$time · 考试 · ${exam.name}", modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                    }
                    if (state.completedCourses.isNotEmpty()) {
                        item(key = "completed") {
                            TextButton(onClick = { showCompleted = !showCompleted }) {
                                val action = if (showCompleted) "收起" else "查看"
                                Text("${action}已结束课程（${state.completedCourses.size}）")
                            }
                        }
                        if (showCompleted) {
                            items(state.completedCourses, key = { it.occurrenceKey }) { item ->
                                CourseCard(
                                    item, isCurrent = false, nextInfo = null, semester = state.semester,
                                    onClick = { onCourseClick(item.course.id, item.schedule.id, item.originalDay) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.semester != null && !state.isEmpty) {
            FloatingActionButton(
                onClick = onAddCourse,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "\u6dfb\u52a0\u8bfe\u7a0b")
            }
        }
    }
}
