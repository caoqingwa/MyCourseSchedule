package com.example.courseschedule.ui.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.courseschedule.ui.component.CalendarPicker
import com.example.courseschedule.util.DateUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onDayClick: (Long) -> Unit,
    onNavigateToToday: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cal = Calendar.getInstance()
    val month = cal.get(Calendar.MONTH)
    val year = cal.get(Calendar.YEAR)

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("课程表", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) })

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                state.semester?.let { sem ->
                    CalendarPicker(
                        currentMonth = month, currentYear = year,
                        todayMillis = state.todayMillis,
                        semesterStartMillis = sem.startDate,
                        currentWeek = state.currentWeek,
                        onDayClick = { onDayClick(it) },
                        onMonthChange = { _, _ -> },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                }
            }

            item {
                Text("本周课程", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val days = listOf("周一", "周二", "周三", "周四", "周五")
                    state.weeklyCourseCount.forEachIndexed { i, count ->
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(10.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(days[i], fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(count.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("节课", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Text("近期考试", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }

            if (state.exams.isEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Text("暂无考试安排", modifier = Modifier.padding(14.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(state.exams) { exam ->
                    val daysLeft = ((exam.examDate - System.currentTimeMillis()) / 86400000).toInt().coerceAtLeast(0)
                    val badgeColor = when { daysLeft <= 7 -> MaterialTheme.colorScheme.error; daysLeft <= 14 -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary }
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exam.notes ?: "考试", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text(SimpleDateFormat("MM月dd日", Locale.getDefault()).format(Date(exam.examDate)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(color = badgeColor, shape = RoundedCornerShape(10.dp)) {
                                Text(" 还有 " + daysLeft + "天 ", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
