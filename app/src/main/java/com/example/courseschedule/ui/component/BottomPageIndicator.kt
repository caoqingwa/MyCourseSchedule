package com.example.courseschedule.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

private val PAGE_LABELS = listOf("\u4eca\u65e5", "\u5468\u8bfe\u8868", "\u65e5\u5386")

/**
 * 底部页面指示器：3个圆角胶囊 + 滑动高亮 + 文字标签。
 * 支持点击圆点切换和在指示器上拖动切换。
 *
 * @param pagerScrollFraction pager 当前滚动分数（0f ~ pageCount-1）
 */
@Composable
fun BottomPageIndicator(
    pageCount: Int,
    currentPage: Int,
    pagerScrollFraction: Float,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 指示器位置：手势时直接 snap，静止时由 LaunchedEffect 插值动画
    var target by remember { mutableFloatStateOf(currentPage.toFloat()) }
    var animFrac by remember { mutableFloatStateOf(currentPage.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    // 非拖动状态：target 变化时做平滑插值动画
    LaunchedEffect(target, isDragging) {
        if (!isDragging) {
            while (true) {
                val diff = target - animFrac
                if (abs(diff) < 0.005f) { animFrac = target; break }
                animFrac += diff * 0.22f
                kotlinx.coroutines.delay(16L) // ~60fps
            }
        }
    }

    val inactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val activeColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current
    val barHeight = 56.dp
    val sidePad = 32.dp
    val gap = 36.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(pageCount) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tapX = down.position.x

                    // 计算圆点布局（与 Canvas 一致）
                    val sidePadPx = with(density) { sidePad.toPx() }
                    val usableW = size.width.toFloat() - sidePadPx * 2
                    val gapPx = with(density) { gap.toPx() }
                    val totalGap = gapPx * (pageCount - 1)
                    val dotDia = (usableW - totalGap) / pageCount
                    val radius = dotDia / 2

                    // 检测是否点击了某个圆点
                    var tappedDot = -1
                    for (i in 0 until pageCount) {
                        val cx = sidePadPx + radius + i * (dotDia + gapPx)
                        if (abs(tapX - cx) < radius + with(density) { 14.dp.toPx() }) {
                            tappedDot = i
                            break
                        }
                    }

                    if (tappedDot >= 0) {
                        // 点击：直接跳转
                        target = tappedDot.toFloat()
                        animFrac = tappedDot.toFloat()
                        isDragging = false
                        onPageSelected(tappedDot)
                        return@awaitEachGesture
                    }

                    // 拖动切换
                    isDragging = true
                    var lastX = tapX
                    var dragAccumPx = 0f
                    var dragged = false
                    val threshold = with(density) { 6.dp.toPx() }

                    while (true) {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull() ?: break
                        val dx = ch.position.x - lastX
                        val dy = ch.position.y - down.position.y

                        if (!dragged) {
                            if (abs(dx) < threshold && abs(dy) < threshold) {
                                if (ch.pressed) { lastX = ch.position.x; continue } else break
                            }
                            if (abs(dy) > with(density) { 20.dp.toPx() }) break
                            dragged = true
                        }

                        if (dragged && ch.pressed) {
                            ch.consume()
                            dragAccumPx += dx
                            val rawFrac = pagerScrollFraction - dragAccumPx / usableW
                            val clamped = rawFrac.coerceIn(0f, (pageCount - 1).toFloat())
                            animFrac = clamped  // 跟手，直接赋值
                            lastX = ch.position.x
                        } else {
                            break
                        }
                    }

                    if (dragged) {
                        // 松手：snap 到最近页
                        val snapTarget = animFrac.roundToInt().coerceIn(0, pageCount - 1).toFloat()
                        target = snapTarget
                        isDragging = false
                        onPageSelected(snapTarget.toInt())
                    } else {
                        isDragging = false
                    }
                }
            }
    ) {
        // 圆点 + 高亮胶囊（Canvas）
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .padding(horizontal = 16.dp)
        ) {
            val sPad = sidePad.toPx()
            val usableW = size.width - sPad * 2
            val g = gap.toPx()
            val totalG = g * (pageCount - 1)
            val dotDia = (usableW - totalG) / pageCount
            val r = dotDia / 2
            val cy = size.height / 2

            // 静态圆点
            for (i in 0 until pageCount) {
                val cx = sPad + r + i * (dotDia + g)
                val color = if (i == currentPage) activeColor else inactiveColor
                drawRoundRect(color, Offset(cx - r, cy - r), Size(dotDia, dotDia), CornerRadius(r))
            }

            // 滑动高亮胶囊
            val pillW = dotDia * 1.08f
            val pillCx = sPad + r + animFrac * (dotDia + g)
            drawRoundRect(
                activeColor,
                Offset(pillCx - pillW / 2, cy - pillW / 2),
                Size(pillW, pillW),
                CornerRadius(pillW / 2)
            )
        }

        // 文字标签（Canvas 外层，通过 Row + SpaceEvenly 精确对齐圆点中心）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(barHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PAGE_LABELS.forEachIndexed { i, label ->
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = if (i == currentPage) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (i == currentPage) onSurfaceColor.copy(alpha = 0.9f)
                    else onSurfaceColor.copy(alpha = 0.4f)
                )
            }
        }
    }
}
