package com.example.courseschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.ui.theme.CourseColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekGrid(
    schedules: List<Schedule>,
    courses: Map<Long, Course>,
    currentDayOfWeek: Int,
    onCellClick: (Course, Schedule) -> Unit,
    onCellLongClick: (dayOfWeek: Int, period: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayNames = listOf("\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94")
    val scheduleMap = mutableMapOf<Pair<Int,Int>, Pair<Course, Schedule>>()

    schedules.forEach { s ->
        if (s.dayOfWeek in 1..5) {
            val course = courses[s.courseId] ?: return@forEach
            for (p in s.startPeriod..s.endPeriod) {
                scheduleMap[(s.dayOfWeek - 1) to (p - 1)] = course to s
            }
        }
    }

    Column(modifier = modifier) {
        Row {
            Spacer(modifier = Modifier.width(36.dp))
            dayNames.forEachIndexed { i, name ->
                val isToday = (i + 1) == currentDayOfWeek
                Box(
                    modifier = Modifier.weight(1f).padding(1.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }

        for (row in 0..11) {
            Row(modifier = Modifier.height(40.dp)) {
                Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                    Text("${row + 1}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                for (col in 0..4) {
                    val pair = scheduleMap[col to row]
                    if (pair != null) {
                        val (course, _) = pair
                        val colorIdx = (course.id % CourseColors.size).toInt()
                        val (bg, fg) = CourseColors[colorIdx]
                        Box(
                            modifier = Modifier.weight(1f).padding(1.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(bg)
                                .clickable { onCellClick(course, pair.second) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                course.name, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                                color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center, lineHeight = 10.sp
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.weight(1f).padding(1.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { onCellLongClick(col + 1, row + 1) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}