package com.example.courseschedule.ui.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.ui.theme.getCourseColorByName
import com.example.courseschedule.util.DateUtils
import kotlinx.coroutines.delay

private data class MergedBlock(val course: Course, val schedule: Schedule, val startPeriod: Int, val endPeriod: Int)
private val FallbackColor = Color.Gray to Color.White
// 课程块与网格线之间的垂直间隙（块首尾各留一半）；连堂块只在整块首尾留间隙，内部节间保持连续
private val BlockGap = 4.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekGrid(
    schedules: List<Schedule>,
    courses: Map<Long, Course>,
    roomMap: Map<Long, String> = emptyMap(),
    semester: Semester?,
    highlightDayOfWeek: Int = 0,
    showNowIndicator: Boolean = false,
    selectedWeek: Int = 1,
    onCellClick: (Course, Schedule) -> Unit,
    onCellLongClick: (dayOfWeek: Int, period: Int) -> Unit,
    onCourseLongClick: (Course, Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekDays = semester?.weekDays ?: 5
    val colCount = weekDays.coerceIn(5, 7)
    val dayNames = if (colCount == 7) DateUtils.DAY_NAMES_7 else DateUtils.DAY_NAMES_5
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val cellShape = RoundedCornerShape(8.dp)
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

    val (startTimes, endTimes) = remember(semester) {
        val times = semester?.getPeriodTimes() ?: Semester.defaultPeriodTimes()
        times.map { it.start } to times.map { it.end }
    }

    // remember key 直接用对象引用（List/Map equals）：同周切回命中缓存，内容变化深比一次；
    // 避免 hashCode 作 key 的碰撞风险与每次重组 O(n) 重算
    val colBlocks = remember(schedules, courses, totalPeriods, colCount) { Array(colCount) { col ->
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

    // 按课程名哈希取色：与课程数量/排序无关，增删课程不会让整张课表重新染色，
    // 且与今日页卡片同色（两处共用 getCourseColorByName）
    val courseColorMap = remember(courses) {
        courses.values.map { it.name }.distinct().associateWith { getCourseColorByName(it) }
    }

    val occupied = remember(schedules, courses, totalPeriods, colCount) {
        Array(totalPeriods) { row ->
            BooleanArray(colCount) { col -> colBlocks[col].any { it.startPeriod <= row + 1 && it.endPeriod >= row + 1 } }
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    // 7 天视图横向空间紧张：收窄节次列、减小块间距与内边距，给课程名让出更多宽度；字号不变，靠增加行数显示更多
    val compact = colCount >= 7
    val periodColWidthDp = if (compact) 32.dp else 40.dp
    val colWidthDp = (screenWidthDp - (if (compact) 32 else 40)) / colCount
    val blockGap = if (compact) 2.dp else BlockGap
    val blockTextPadding = if (compact) 1.dp else 2.dp
    val blockNameFontSize = 10.sp
    val blockLineHeight = 13.sp
    val density = LocalDensity.current

    // 「现在」标记：只点亮左侧节次列，不做满宽硬线；每 30 秒重算一次，仅当前周显示
    var nowMinuteOfDay by remember { mutableIntStateOf(currentMinuteOfDay()) }
    LaunchedEffect(showNowIndicator) {
        if (!showNowIndicator) return@LaunchedEffect
        while (true) {
            nowMinuteOfDay = currentMinuteOfDay()
            delay(30_000)
        }
    }
    val nowLineOffset = remember(nowMinuteOfDay, startTimes, endTimes, showNowIndicator) {
        if (!showNowIndicator) null else nowLinePeriodOffset(nowMinuteOfDay, startTimes, endTimes)
    }
    // 当前所在节次（0 基下标）；课间、放学后或非当前周为 null
    val nowPeriodIndex = nowLineOffset?.toInt()

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
                    // 只有「当前周 + 今天」才高亮：别的周传 0，绝不能再回退到今天的星期几，
                    // 否则翻到第 8 周时仍把「周四」标成今天（日期数字也跟着错）
                    val isToday = highlightDayOfWeek > 0 && dayNum == highlightDayOfWeek
                    Box(
                        modifier = Modifier
                            .width(colWidthDp.dp).fillMaxHeight()
                            .then(
                                if (i > 0) Modifier.border(0.5.dp, dateBorderColor, RoundedCornerShape(0.dp))
                                else Modifier
                            )
                            .background(
                                if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else MaterialTheme.colorScheme.surfaceContainer,
                                cellShape
                            ),
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
                    .border(0.8.dp, borderColor, cellShape),
                contentAlignment = Alignment.Center
            ) {
                Text("\u8282\u6b21", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            for (i in dayNames.indices) {
                val dayNum = i + 1
                val isHighlighted = highlightDayOfWeek > 0 && dayNum == highlightDayOfWeek
                Box(
                    modifier = Modifier.width(colWidthDp.dp).fillMaxHeight()
                        .border(0.8.dp, borderColor, cellShape)
                        .background(
                            if (isHighlighted) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                            cellShape
                        ),
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
            // Period labels (left column) — 当前所在节次的节号与时间用主色点亮
            for (row in 0 until totalPeriods) {
                val sTime = startTimes.getOrNull(row) ?: "??"
                val eTime = endTimes.getOrNull(row) ?: "??"
                val isNowPeriod = row == nowPeriodIndex
                Box(
                    modifier = Modifier
                        .offset(x = 0.dp, y = cellHeight * row)
                        .size(periodColWidthDp, cellHeight)
                        .background(
                            if (isNowPeriod) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent,
                            cellShape
                        )
                        .border(
                            0.8.dp,
                            if (isNowPeriod) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            else borderColor,
                            cellShape
                        )
                ) {
                    // 左侧短竖条：比满宽红线更含蓄的「此刻」锚点
                    if (isNowPeriod) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(3.dp)
                                .fillMaxHeight(0.45f)
                                .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${row + 1}",
                            fontSize = if (isNowPeriod) 11.sp else 10.sp,
                            color = if (isNowPeriod) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isNowPeriod) FontWeight.Bold else FontWeight.Medium
                        )
                        Text(
                            sTime,
                            fontSize = if (isNowPeriod) 8.sp else 7.sp,
                            color = if (isNowPeriod) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            fontWeight = if (isNowPeriod) FontWeight.SemiBold else null,
                            lineHeight = 9.sp
                        )
                        Text(
                            eTime,
                            fontSize = if (isNowPeriod) 8.sp else 7.sp,
                            color = if (isNowPeriod) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            fontWeight = if (isNowPeriod) FontWeight.SemiBold else null,
                            lineHeight = 9.sp
                        )
                    }
                }
            }

            // 内容区不绘制网格线，仅保留节次列边框与表头，课程块之间以间隙+颜色区分

            // 当前节次整行极淡底色：帮助横向定位「现在」，不遮挡课程块（块画在其上）
            if (nowPeriodIndex != null) {
                Box(
                    modifier = Modifier
                        .offset(x = periodColWidthDp, y = cellHeight * nowPeriodIndex)
                        .width(colWidthDp.dp * colCount)
                        .height(cellHeight)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
                )
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
                val blocks = colBlocks[col]
                var prevEnd = 0
                for (block in blocks) {
                    // 与前一块时间重叠时半宽并排，避免后画的块完全盖住先画的
                    val overlaps = block.startPeriod <= prevEnd
                    val x = periodColWidthDp + colWidthDp.dp * col + blockGap / 2 +
                            if (overlaps) colWidthDp.dp * 0.5f else 0.dp
                    val blockWidth = colWidthDp.dp - blockGap -
                            if (overlaps) colWidthDp.dp * 0.5f else 0.dp
                    val y = cellHeight * (block.startPeriod - 1) + blockGap / 2
                    val span = block.endPeriod - block.startPeriod + 1
                    val blockHeight = cellHeight * span - blockGap
                    val (bg, fg) = courseColorMap[block.course.name] ?: FallbackColor
                    prevEnd = maxOf(prevEnd, block.endPeriod)

                    Box(
                        modifier = Modifier
                            .offset(x, y)
                            .size(blockWidth, blockHeight)
                            .clip(cellShape)
                            .background(bg)
                            .combinedClickable(
                                onClick = { onCellClick(block.course, block.schedule) },
                                onLongClick = { onCourseLongClick(block.course, block.schedule) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val roomName = block.schedule.roomId?.let { roomMap[it] } ?: ""
                            val displayName = if (roomName.isNotBlank()) block.course.name + "@" + roomName else block.course.name
                            // 按方块实际可用高度自适应行数，尽量把课程名完整显示（原来固定 3 行会过早省略）
                            val labelHeight = if (span > 1) with(density) { 10.sp.toDp() } else 0.dp
                            val maxLines = ((blockHeight - blockTextPadding * 2 - labelHeight) /
                                    with(density) { blockLineHeight.toDp() })
                                .toInt().coerceIn(1, 8)
                            Text(
                                displayName,
                                fontSize = blockNameFontSize,
                                fontWeight = FontWeight.SemiBold,
                                color = fg,
                                maxLines = maxLines,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                lineHeight = blockLineHeight,
                                modifier = Modifier.padding(blockTextPadding)
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

            // 「现在」位置由左侧节次列点亮 + 当前行淡色底标示，不再画满宽红线
        }
    }
}

/** 当前时间的「分钟数」表示（0..1439） */
private fun currentMinuteOfDay(): Int {
    val cal = java.util.Calendar.getInstance()
    return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
}

private fun parseTimeToMinutes(raw: String?): Int? {
    if (raw == null) return null
    val parts = raw.split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

/** 返回「第几节 + 节内进度」形式的纵向位置；不在任何节次区间内（课间/放学后）返回 null */
private fun nowLinePeriodOffset(
    nowMinutes: Int,
    startTimes: List<String>,
    endTimes: List<String>
): Float? {
    for (i in startTimes.indices) {
        val s = parseTimeToMinutes(startTimes.getOrNull(i)) ?: continue
        val e = parseTimeToMinutes(endTimes.getOrNull(i)) ?: continue
        if (nowMinutes in s until e) {
            val span = (e - s).coerceAtLeast(1)
            return i + (nowMinutes - s).toFloat() / span
        }
    }
    return null
}
