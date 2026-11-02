package com.example.courseschedule.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

// ── 品牌浅色 ──
val Primary = Color(0xFF4A6741)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFCBE8BE)
val OnPrimaryContainer = Color(0xFF082100)
val Surface = Color(0xFFF8FAF0)
val OnSurface = Color(0xFF1A1C18)
val OnSurfaceVariant = Color(0xFF43483E)
val Outline = Color(0xFF73796D)
val SurfaceContainer = Color(0xFFECF0E3)
val SurfaceContainerLow = Color(0xFFF2F6E9)

// ── 品牌深色（沿用绿色主色系，深底上把主色提亮）──
val DarkPrimary = Color(0xFFB3D6A5)
val DarkOnPrimary = Color(0xFF1E370F)
val DarkPrimaryContainer = Color(0xFF354E2B)
val DarkOnPrimaryContainer = Color(0xFFCEEBBF)
val DarkSurface = Color(0xFF11140D)
val DarkOnSurface = Color(0xFFE3E4D9)
val DarkOnSurfaceVariant = Color(0xFFC2C8B9)
val DarkOutline = Color(0xFF8C9285)
val DarkSurfaceContainer = Color(0xFF1D211A)
val DarkSurfaceContainerLow = Color(0xFF181B15)

/** 课程块配色：同一色相下「浅底 + 深字」。 */
private fun courseColorsForHue(hue: Float): Pair<Color, Color> =
    // 实测最差对比度约 6.2:1（白字原方案只有 1.7~2.6:1，远低于 4.5:1）
    hslToColor(hue, 0.45f, 0.72f) to hslToColor(hue, 0.50f, 0.18f)

/**
 * 根据单个课程名生成稳定的颜色（与课程数量、排序、增删其它课程都无关）：
 * 同名同色，不同名大概率不同色。今日页与周课表共用这一套，避免两页颜色不一致、
 * 以及旧实现（按集合索引均分色相）在新增/删除课程时整张课表重新染色。
 */
fun getCourseColorByName(courseName: String): Pair<Color, Color> {
    val raw = courseName.hashCode().toLong() and 0xFFFFFFFFL
    // 略作打散，让相邻课程名的色相差得更开
    val spread = (raw * 2654435761L and 0xFFFFFFFFL) % 360L
    var hue = ((raw % 360L + spread) % 360L).toFloat()
    // 跳过黄色系(45~65)：浅黄底 + 深字发脏，且与「提醒/警示」色太像
    if (hue in 45f..65f) hue = (hue + 40f) % 360f
    return courseColorsForHue(hue)
}

/** HSL → Compose Color，h∈[0,360), s/l∈[0,1] */
private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60  -> floatArrayOf(c, x, 0f)
        h < 120 -> floatArrayOf(x, c, 0f)
        h < 180 -> floatArrayOf(0f, c, x)
        h < 240 -> floatArrayOf(0f, x, c)
        h < 300 -> floatArrayOf(x, 0f, c)
        else    -> floatArrayOf(c, 0f, x)
    }
    return Color(
        red   = ((r + m) * 255).roundToInt().coerceIn(0, 255),
        green = ((g + m) * 255).roundToInt().coerceIn(0, 255),
        blue  = ((b + m) * 255).roundToInt().coerceIn(0, 255)
    )
}
