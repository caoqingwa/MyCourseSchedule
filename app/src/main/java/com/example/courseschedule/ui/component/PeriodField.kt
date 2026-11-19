package com.example.courseschedule.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 起节 / 正节输入框：
 * - 点按 → 弹数字键盘直接输入；
 * - 长按后上下拖动 → 逐节增减（22dp 一格），松手即落定；
 * - 右侧竖条是节次滑轨：每节一个刻度，滑块位置对应当前值。
 */
@Composable
internal fun PeriodField(
    label: String,
    valueText: String,
    onValueTextChange: (String) -> Unit,
    periodCount: Int,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val range = 1..periodCount.coerceAtLeast(1)
    val value = valueText.toIntOrNull()?.coerceIn(range) ?: range.first
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueTextChange)
    var editing by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(4.dp)
    val active = editing || scrubbing
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(shape)
                .border(if (active) 1.6.dp else 1.dp, borderColor, shape)
                .pointerInput(editing, periodCount) {
                    if (editing) return@pointerInput
                    val stepPx = with(density) { 22.dp.toPx() }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 从按下的第一刻就吃掉事件：否则手指在等待长按期间的那点位移
                        // 会让外层 Column 的 verticalScroll 抢先滚动，滑到边界时整页回弹闪烁
                        down.consume()
                        var tapped = false
                        val resolvedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) {
                                    tapped = true
                                    break
                                }
                                val moved = (change.position - down.position).getDistance()
                                if (moved > viewConfiguration.touchSlop) break
                            }
                            true
                        }
                        if (resolvedEarly != null) {
                            if (tapped) editing = true
                            return@awaitEachGesture
                        }
                        scrubbing = true
                        val startValue = latestValue
                        var lastReported = startValue
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                val delta = ((change.position.y - down.position.y) / stepPx).toInt()
                                val next = (startValue + delta).coerceIn(range)
                                if (next != lastReported) {
                                    lastReported = next
                                    latestOnChange(next.toString())
                                }
                                if (!change.pressed) break
                            }
                        } finally {
                            scrubbing = false
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)
            ) {
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (editing) {
                    BasicTextField(
                        value = valueText,
                        onValueChange = { raw -> onValueTextChange(raw.filter { it.isDigit() }.take(2)) },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (!it.isFocused) editing = false }
                    )
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                } else {
                    Text(
                        if (valueText.isBlank()) "-" else valueText,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            PeriodRail(
                value = value,
                range = range,
                active = active,
                modifier = Modifier
                    .padding(end = 7.dp, top = 8.dp, bottom = 8.dp)
                    .width(13.dp)
                    .fillMaxHeight()
            )
        }
        if (isError) {
            Text(
                "限制: 1~${periodCount}节",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
        }
    }
}

/** 节次滑轨：细竖线 + 每节一个刻度 + 当前值滑块 */
@Composable
private fun PeriodRail(
    value: Int,
    range: IntRange,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.outline.copy(alpha = if (active) 0.6f else 0.35f)
    val thumb = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    Canvas(modifier = modifier) {
        val n = range.last - range.first + 1
        val cx = size.width / 2f
        val thumbH = 11.dp.toPx()
        val travel = size.height - thumbH
        drawLine(track, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.dp.toPx())
        val tickHalf = 2.dp.toPx()
        for (i in 0 until n) {
            val y = if (n > 1) size.height * i / (n - 1).toFloat() else 0f
            drawLine(track, Offset(cx - tickHalf, y), Offset(cx + tickHalf, y), strokeWidth = 1.dp.toPx())
        }
        val t = if (n > 1) (value - range.first) / (n - 1).toFloat() else 0f
        drawRoundRect(
            color = thumb,
            topLeft = Offset(cx - size.width * 0.3f, travel * t),
            size = Size(size.width * 0.6f, thumbH),
            cornerRadius = CornerRadius(thumbH / 2f)
        )
    }
}
