package com.example.courseschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.ui.theme.CourseColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekGrid(
    schedules: List<Schedule>,
    courses: Map<Long, Course>,
    roomMap: Map<Long, String> = emptyMap(),
    semester: Semester?,
    currentDayOfWeek: Int,
    highlightDayOfWeek: Int = 0,
    onCellClick: (Course, Schedule) -> Unit,
    onCellLongClick: (dayOfWeek: Int, period: Int) -> Unit,
    onCourseLongClick: (Course, Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayNames = listOf("\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94")
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val cellShape = RoundedCornerShape(2.dp)
    val cellHeight = 44.dp
    val headerHeight = 28.dp
    val periodColWidth = 52.dp
    val totalPeriods = semester?.periodCount ?: 12
    val gridBodyHeight = cellHeight * totalPeriods

    val startTimes = semester?.getStartTimes() ?: Semester.defaultPeriodTimes().map { it.start }
    val endTimes = semester?.getEndTimes() ?: Semester.defaultPeriodTimes().map { it.end }

    data class MergedBlock(val course: Course, val schedule: Schedule, val startPeriod: Int, val endPeriod: Int)
    val schedulesKey = schedules.hashCode()
    val coursesKey = courses.keys.hashCode()
    val colBlocks = remember(schedulesKey, coursesKey, totalPeriods) { Array(5) { col ->
        val daySchedules = schedules.filter { it.dayOfWeek == col + 1 }.sortedBy { it.startPeriod }
        val groups = daySchedules.groupBy { it.courseId * 100000 + it.weekType * 10000 + it.startWeek * 100 + it.endWeek }
        val result = mutableListOf<MergedBlock>()
        for (groupSchedules in groups.values) {
            val merged = mutableListOf<Pair<Int, Int>>()
            for (s in groupSchedules) {
                val last = merged.lastOrNull()
                if (last != null && last.second + 1 == s.startPeriod) merged[merged.lastIndex] = last.first to s.endPeriod
                else merged.add(s.startPeriod to s.endPeriod)
            }
            val course = courses[groupSchedules.first().courseId] ?: continue
            val base = groupSchedules.first()
            for ((sp, ep) in merged) result.add(MergedBlock(course, base.copy(startPeriod = sp, endPeriod = ep), sp, ep))
        }
        result.sortedBy { it.startPeriod } } }

    val occupied = remember(colBlocks.map { it.size }, totalPeriods) {
        Array(totalPeriods) { row ->
            BooleanArray(5) { col -> colBlocks[col].any { it.startPeriod <= row + 1 && it.endPeriod >= row + 1 } }
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val colWidthDp = (screenWidthDp - 52) / 5

    Column(modifier = modifier) {
        // Header row
        Row(modifier = Modifier.fillMaxWidth().height(headerHeight)) {
            Box(
                modifier = Modifier.width(periodColWidth).fillMaxHeight()
                    .border(0.8.dp, borderColor, cellShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("\u8282\u6b21", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            for (i in dayNames.indices) {
                val dayNum = i + 1
                val isHighlighted = if (highlightDayOfWeek > 0) dayNum == highlightDayOfWeek
                    else dayNum == currentDayOfWeek
                Box(
                    modifier = Modifier.width(colWidthDp.dp).fillMaxHeight()
                        .border(0.8.dp, borderColor, cellShape)
                        .background(if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dayNames[i], fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (isHighlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Grid body with fixed height
        Box(modifier = Modifier.fillMaxWidth().height(gridBodyHeight)) {
            // Layer 1: Period numbers + times
            for (row in 0 until totalPeriods) {
                val sTime = startTimes.getOrNull(row) ?: "??"
                val eTime = endTimes.getOrNull(row) ?: "??"
                Box(
                    modifier = Modifier
                        .offset(x = 0.dp, y = cellHeight * row)
                        .size(periodColWidth, cellHeight)
                        .border(0.8.dp, borderColor, cellShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${row + 1}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        Text(sTime, fontSize = 6.sp, color = MaterialTheme.colorScheme.outline, lineHeight = 7.sp)
                        Text(eTime, fontSize = 6.sp, color = MaterialTheme.colorScheme.outline, lineHeight = 7.sp)
                    }
                }
            }

            // Layer 2: Empty cells (background grid with touch)
            for (row in 0 until totalPeriods) {
                for (col in 0..4) {
                    val x = periodColWidth + colWidthDp.dp * col
                    val y = cellHeight * row
                    if (!occupied[row][col]) {
                        Box(
                            modifier = Modifier
                                .offset(x, y)
                                .size(colWidthDp.dp, cellHeight)
                                .border(0.8.dp, borderColor, cellShape)
                                .clip(cellShape)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { onCellLongClick(col + 1, row + 1) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {}
                    }
                }
            }

            // Layer 3: Merged course blocks (on top)
            for (col in 0..4) {
                for (block in colBlocks[col]) {
                    val x = periodColWidth + colWidthDp.dp * col
                    val y = cellHeight * (block.startPeriod - 1)
                    val span = block.endPeriod - block.startPeriod + 1
                    val blockHeight = cellHeight * span
                    val colorIdx = (block.course.id % CourseColors.size).toInt()
                    val (bg, fg) = CourseColors[colorIdx]

                    Box(
                        modifier = Modifier
                            .offset(x, y)
                            .size(colWidthDp.dp, blockHeight)
                            .clip(cellShape)
                            .background(bg)
                            .combinedClickable(
                                onClick = { onCellClick(block.course, block.schedule) },
                                onLongClick = { onCourseLongClick(block.course, block.schedule) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val roomName = block.course.roomId?.let { roomMap[it] } ?: ""
                            val displayName = if (roomName.isNotBlank()) block.course.name + "@" + roomName else block.course.name
                            Text(
                                displayName,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = fg,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                lineHeight = 11.sp,
                                modifier = Modifier.padding(2.dp)
                            )
                            if (span > 1) {
                                Text(
                                    "\u7b2c${block.startPeriod}-${block.endPeriod}\u8282",
                                    fontSize = 7.sp,
                                    color = fg.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
