package com.example.courseschedule.ui.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val cal = remember { Calendar.getInstance() }
    var displayMonth by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) }
    var displayYear by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }

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
                        onDayClick = { onDayClick(it) },
                        onMonthChange = { month, year -> displayMonth = month; displayYear = year },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                }
            }

            item {
                Text(
                    "\u672c\u5468\u8bfe\u7a0b",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val days = listOf("\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94")
                    state.weeklyCourseCount.forEachIndexed { i, count ->
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(days[i], fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(count.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("\u8282\u8bfe", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Text(
                    "\u8fd1\u671f\u8003\u8bd5",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                items(state.exams) { exam ->
                    val daysLeft = ((exam.examDate - System.currentTimeMillis()) / 86400000).toInt().coerceAtLeast(0)
                    val badgeColor = when {
                        daysLeft <= 7 -> MaterialTheme.colorScheme.error
                        daysLeft <= 14 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exam.notes ?: "\u8003\u8bd5", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                val examCal = Calendar.getInstance().apply { timeInMillis = exam.examDate }
                                val dayOfWeekNames = listOf("\u5468\u65e5", "\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94", "\u5468\u516d")
                                Text(
                                    SimpleDateFormat("M\u6708d\u65e5 \u00b7 ", Locale.getDefault()).format(Date(exam.examDate)) +
                                            dayOfWeekNames[examCal.get(Calendar.DAY_OF_WEEK) - 1],
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(color = badgeColor, shape = RoundedCornerShape(10.dp)) {
                                Text(
                                    " \u8fd8\u6709 " + daysLeft + "\u5929 ",
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
}
