package com.example.courseschedule.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.util.DateUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarPicker(
    currentMonth: Int,
    currentYear: Int,
    todayMillis: Long,
    semesterStartMillis: Long,
    currentWeek: Int,
    totalWeeks: Int,
    onDayClick: (Long) -> Unit,
    onMonthChange: (Int, Int) -> Unit,
    onDayLongPress: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dows = listOf("\u4e00", "\u4e8c", "\u4e09", "\u56db", "\u4e94", "\u516d", "\u65e5")
    // 跟手位移：拖动实时平移月历（graphicsLayer 合成变换，无重绘）
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 60f
    val scope = rememberCoroutineScope()

    val semStartCal = remember(semesterStartMillis) {
        Calendar.getInstance().apply { timeInMillis = semesterStartMillis }
    }
    val semStartDate = remember(semesterStartMillis) {
        semStartCal.get(Calendar.YEAR) * 100 + semStartCal.get(Calendar.MONTH)
    }
    val semEndMillis = remember(semesterStartMillis, totalWeeks) {
        semesterStartMillis + totalWeeks.toLong() * 7 * MILLIS_PER_DAY
    }
    val semEndCal = remember(semEndMillis) {
        Calendar.getInstance().apply { timeInMillis = semEndMillis - MILLIS_PER_DAY }
    }
    val semEndDate = remember(semEndMillis) {
        semEndCal.get(Calendar.YEAR) * 100 + semEndCal.get(Calendar.MONTH)
    }

    fun prevMonth() {
        val m = if (currentMonth == 0) 11 else currentMonth - 1
        val y = if (currentMonth == 0) currentYear - 1 else currentYear
        if (y * 100 + m >= semStartDate) onMonthChange(m, y)
    }

    fun nextMonth() {
        val m = if (currentMonth == 11) 0 else currentMonth + 1
        val y = if (currentMonth == 11) currentYear + 1 else currentYear
        if (y * 100 + m <= semEndDate) onMonthChange(m, y)
    }

    val canGoPrev = (currentYear * 100 + currentMonth) > semStartDate
    val canGoNext = (currentYear * 100 + currentMonth) < semEndDate
    // 月历滑出距离：接近整宽，保证甩出/滑入全程跟手
    val density = LocalDensity.current
    val slideDistancePx = with(density) {
        (LocalConfiguration.current.screenWidthDp * 0.9f).dp.toPx()
    }

    // 把 dragOffset 动画到 target（甩出用 tween 快动，回弹用 spring）
    suspend fun animateDragTo(target: Float, springBack: Boolean = false) {
        androidx.compose.animation.core.animate(
            initialValue = dragOffset,
            targetValue = target,
            initialVelocity = 0f,
            animationSpec = if (springBack) {
                spring(dampingRatio = 0.7f, stiffness = 350f)
            } else {
                tween(durationMillis = 160)
            }
        ) { value, _ -> dragOffset = value }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
            .pointerInput(currentMonth, currentYear, canGoPrev, canGoNext) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val over = dragOffset > swipeThreshold
                        val under = dragOffset < -swipeThreshold
                        scope.launch {
                            when {
                                over && canGoPrev -> {
                                    // 甩出当前月 → 切上一月 → 新月滑入
                                    animateDragTo(slideDistancePx)
                                    prevMonth()
                                    dragOffset = -slideDistancePx
                                    animateDragTo(0f)
                                }
                                under && canGoNext -> {
                                    animateDragTo(-slideDistancePx)
                                    nextMonth()
                                    dragOffset = slideDistancePx
                                    animateDragTo(0f)
                                }
                                else -> animateDragTo(0f, springBack = true)
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { animateDragTo(0f, springBack = true) }
                    },
                    onHorizontalDrag = { _, dragAmount -> dragOffset += dragAmount }
                )
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "\u25c0",
                modifier = Modifier.clickable(enabled = canGoPrev) { prevMonth() }
                    .padding(8.dp),
                fontSize = 16.sp,
                color = if (canGoPrev) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            Text(
                currentYear.toString() + "\u5e74" + (currentMonth + 1).toString() + "\u6708",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                "\u25b6",
                modifier = Modifier.clickable(enabled = canGoNext) { nextMonth() }
                    .padding(8.dp),
                fontSize = 16.sp,
                color = if (canGoNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            dows.forEach {
                Text(
                    it, modifier = Modifier.weight(1f), fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val monthKey = currentYear * 100 + currentMonth
        // 位移由外层 dragOffset 驱动（graphicsLayer 合成变换），内容切换仅淡入淡出避免双重位移
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = dragOffset }
        ) {
            AnimatedContent(
                targetState = monthKey,
                transitionSpec = {
                    fadeIn(tween(durationMillis = 140)).togetherWith(fadeOut(tween(durationMillis = 140)))
                },
                label = "monthFade"
            ) {
                MonthGrid(currentYear, currentMonth, todayMillis, semesterStartMillis, currentWeek, onDayClick, onDayLongPress)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val weekRangeText = remember(semesterStartMillis, currentWeek) {
            val startMs = semesterStartMillis + (currentWeek - 1).toLong() * 7 * MILLIS_PER_DAY
            val endMs = startMs + 6 * MILLIS_PER_DAY
            val fmt1 = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
            val fmt2 = SimpleDateFormat("MM.dd", Locale.getDefault())
            "\u5f53\u524d\uff1a\u7b2c$currentWeek\u5468 \u00b7 ${fmt1.format(Date(startMs))} ~ ${fmt2.format(Date(endMs))}"
        }
        Text(
            weekRangeText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthGrid(
    year: Int,
    month: Int,
    todayMillis: Long,
    semesterStartMillis: Long,
    currentWeek: Int,
    onDayClick: (Long) -> Unit,
    onDayLongPress: ((Long) -> Unit)? = null
) {
    val cal = remember(year, month) {
        Calendar.getInstance().apply {
            set(year, month, 1)
        }
    }
    val firstDayOfWeek = remember(year, month) { (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 }
    val daysInMonth = remember(year, month) {
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.get(Calendar.DAY_OF_MONTH)
    }

    val dayData = remember(year, month, todayMillis, semesterStartMillis, currentWeek, daysInMonth) {
        val todayCal = Calendar.getInstance()
        val semCal = Calendar.getInstance().apply { timeInMillis = semesterStartMillis }
        val todayYear = todayCal.get(Calendar.YEAR)
        val todayMonth = todayCal.get(Calendar.MONTH)
        val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

        val result = mutableListOf<Triple<Int, Boolean, Boolean>>()
        for (d in 1..daysInMonth) {
            val c = Calendar.getInstance().apply { set(year, month, d) }
            val millis = c.timeInMillis
            val isToday = c.get(Calendar.YEAR) == todayYear &&
                    c.get(Calendar.MONTH) == todayMonth &&
                    c.get(Calendar.DAY_OF_MONTH) == todayDay
            val weekNum = DateUtils.getWeekNumber(millis, semesterStartMillis)
            result.add(Triple(d, isToday, weekNum == currentWeek))
        }
        result
    }

    Column {
        var dayIdx = 0
        for (row in 0..5) {
            if (dayIdx >= daysInMonth) break
            Row {
                for (col in 0..6) {
                    if (row == 0 && col < firstDayOfWeek || dayIdx >= daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f).height(32.dp))
                    } else {
                        val (d, isToday, inWeek) = dayData[dayIdx]
                        val cal2 = remember(year, month, d) { Calendar.getInstance().apply { set(year, month, d) } }
                        val dayMillis = cal2.timeInMillis
                        val bg = when {
                            isToday -> MaterialTheme.colorScheme.primary
                            inWeek -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        }
                        val fg = when {
                            isToday -> MaterialTheme.colorScheme.onPrimary
                            inWeek -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        if (onDayLongPress != null) {
                            Box(
                                modifier = Modifier.weight(1f).height(32.dp)
                                    .clip(CircleShape)
                                    .background(bg)
                                    .combinedClickable(
                                        onClick = { onDayClick(dayMillis) },
                                        onLongClick = { onDayLongPress(dayMillis) }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(d.toString(), fontSize = 12.sp, color = fg)
                            }
                        } else {
                            Box(
                                modifier = Modifier.weight(1f).height(32.dp)
                                    .clip(CircleShape)
                                    .background(bg)
                                    .clickable { onDayClick(dayMillis) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(d.toString(), fontSize = 12.sp, color = fg)
                            }
                        }
                        dayIdx++
                    }
                }
            }
        }
    }
}