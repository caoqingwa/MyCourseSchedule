package com.example.courseschedule.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.ui.theme.buildCourseColorMap
import com.example.courseschedule.util.DateUtils

private data class MergedBlock(val course: Course, val schedule: Schedule, val startPeriod: Int, val endPeriod: Int)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekGrid(
    schedules: List<Schedule>,
    courses: Map<Long, Course>,
    roomMap: Map<Long, String> = emptyMap(),
    semester: Semester?,
    currentDayOfWeek: Int,
    highlightDayOfWeek: Int = 0,
    selectedWeek: Int = 1,
    onCellClick: (Course, Schedule) -> Unit,
    onCellLongClick: (dayOfWeek: Int, period: Int) -> Unit,
    onCourseLongClick: (Course, Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekDays = semester?.weekDays ?: 5
    val dayNames = if (weekDays == 7) DateUtils.DAY_NAMES_7 else DateUtils.DAY_NAMES_5
    val colCount = weekDays.coerceIn(5, 7)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val cellShape = RoundedCornerShape(2.dp)
    val cellHeight = 80.dp
    val headerHeight = 30.dp
    val dateRowHeight = 22.dp
    val totalPeriods = semester?.periodCount ?: 12
    val gridBodyHeight = cellHeight * totalPeriods

    // Compute dates for each day of the selected week
    val weekDates = remember(semester?.startDate, selectedWeek, colCount) {
        if (semester == null) emptyList()
        else {
            val (weekStart, _) = DateUtils.getWeekRange(semester.startDate, selectedWeek)
            (0 until colCount).map { dayOffset ->
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = weekStart
                    add(java.util.Calendar.DAY_OF_MONTH, dayOffset)
                }
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            }
        }
    }

    // Get month for the period column header
    val monthText = remember(semester?.startDate, selectedWeek) {
        if (semester == null) ""
        else {
            val (weekStart, _) = DateUtils.getWeekRange(semester.startDate, selectedWeek)
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = weekStart }
            "${cal.get(java.util.Calendar.MONTH) + 1}\u6708"
        }
    }

    val startTimes = semester?.getStartTimes() ?: Semester.defaultPeriodTimes().map { it.start }
    val endTimes = semester?.getEndTimes() ?: Semester.defaultPeriodTimes().map { it.end }

    val schedulesKey = schedules.hashCode()
    val coursesKey = courses.hashCode()
    val colBlocks = remember(schedulesKey, coursesKey, totalPeriods, colCount) { Array(colCount) { col ->
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

    // 为所有课程名生成唯一颜色映射
    val courseColorMap = remember(coursesKey) {
        buildCourseColorMap(courses.values.map { it.name })
    }

    val occupied = remember(colBlocks.map { it.size }, totalPeriods, colCount) {
        Array(totalPeriods) { row ->
            BooleanArray(colCount) { col -> colBlocks[col].any { it.startPeriod <= row + 1 && it.endPeriod >= row + 1 } }
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val periodColWidthDp = 40.dp
    val colWidthDp = (screenWidthDp - 40) / colCount

    Column(modifier = modifier) {
        // Date row — show actual dates with light dividers
        if (weekDates.isNotEmpty()) {
            val dateBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            Row(modifier = Modifier.fillMaxWidth().height(dateRowHeight)) {
                Box(
                    modifier = Modifier.width(periodColWidthDp).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(monthText, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                for (i in weekDates.indices) {
                    val dayNum = i + 1
                    val isToday = if (highlightDayOfWeek > 0) dayNum == highlightDayOfWeek
                        else dayNum == currentDayOfWeek
                    Box(
                        modifier = Modifier
                            .width(colWidthDp.dp).fillMaxHeight()
                            .then(
                                if (i > 0) Modifier.border(0.5.dp, dateBorderColor, RoundedCornerShape(0.dp))
                                else Modifier
                            )
                            .background(if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${weekDates[i]}", fontSize = 12.sp,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Header row — show day names
        Row(modifier = Modifier.fillMaxWidth().height(headerHeight)) {
            Box(
                modifier = Modifier.width(periodColWidthDp).fillMaxHeight()
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

        // Grid body: Canvas for grid lines + period labels + touch overlay + course blocks
        Box(modifier = Modifier.fillMaxWidth().height(gridBodyHeight)) {
            // Period labels (left column) — rendered first, under grid lines
            for (row in 0 until totalPeriods) {
                val sTime = startTimes.getOrNull(row) ?: "??"
                val eTime = endTimes.getOrNull(row) ?: "??"
                Box(
                    modifier = Modifier
                        .offset(x = 0.dp, y = cellHeight * row)
                        .size(periodColWidthDp, cellHeight)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(0.8.dp, borderColor, cellShape)
                ) {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${row + 1}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        Text(sTime, fontSize = 7.sp, color = MaterialTheme.colorScheme.outline, lineHeight = 9.sp)
                        Text(eTime, fontSize = 7.sp, color = MaterialTheme.colorScheme.outline, lineHeight = 9.sp)
                    }
                }
            }

            // Draw grid lines in the course area only (right of period column)
            Canvas(
                modifier = Modifier
                    .offset(x = periodColWidthDp, y = 0.dp)
                    .size(colWidthDp.dp * colCount, gridBodyHeight)
            ) {
                val cellW = colWidthDp.dp.toPx()
                val cellH = cellHeight.toPx()
                val lineColor = borderColor

                // Vertical lines (between columns)
                for (col in 0..colCount) {
                    val x = cellW * col
                    drawLine(lineColor, Offset(x, 0f), Offset(x, cellH * totalPeriods), strokeWidth = 1.5f)
                }
                // Horizontal lines
                for (row in 0..totalPeriods) {
                    val y = cellH * row
                    drawLine(lineColor, Offset(0f, y), Offset(cellW * colCount, y), strokeWidth = 1.5f)
                }
            }

            // Touch overlay — handles empty cell long-press, placed BEFORE course blocks
            // so course blocks (rendered after) are on top and receive events first
            Box(
                modifier = Modifier
                    .offset(x = periodColWidthDp, y = 0.dp)
                    .size(colWidthDp.dp * colCount, gridBodyHeight)
                    .pointerInput(totalPeriods, colCount) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                val col = (offset.x / colWidthDp.dp.toPx()).toInt().coerceIn(0, colCount - 1)
                                val row = (offset.y / cellHeight.toPx()).toInt().coerceIn(0, totalPeriods - 1)
                                if (!occupied[row][col]) {
                                    onCellLongClick(col + 1, row + 1)
                                }
                            }
                        )
                    }
            )

            // Course blocks — rendered AFTER touch overlay so they are on top and receive events first
            for (col in 0 until colCount) {
                for (block in colBlocks[col]) {
                    val x = periodColWidthDp + colWidthDp.dp * col
                    val y = cellHeight * (block.startPeriod - 1)
                    val span = block.endPeriod - block.startPeriod + 1
                    val blockHeight = cellHeight * span
                    val (bg, fg) = courseColorMap[block.course.name]
                        ?: (androidx.compose.ui.graphics.Color.Gray to androidx.compose.ui.graphics.Color.White)

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
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = fg,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                lineHeight = 13.sp,
                                modifier = Modifier.padding(2.dp)
                            )
                            if (span > 1) {
                                Text(
                                    "\u7b2c${block.startPeriod}-${block.endPeriod}\u8282",
                                    fontSize = 8.sp,
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
