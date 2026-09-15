package com.example.courseschedule.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

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

/**
 * 根据一组课程名，生成 名称→(背景色, 前景色) 的映射。
 * 同名课程必同色，不同名必不同色。
 * 利用 HSL 色相环均匀分配色相，饱和度/亮度柔和。
 */
fun buildCourseColorMap(courseNames: Collection<String>): Map<String, Pair<Color, Color>> {
    val unique = courseNames.toSortedSet()
    val map = mutableMapOf<String, Pair<Color, Color>>()
    val count = unique.size
    if (count == 0) return map
    var idx = 0
    for (name in unique) {
        // 色相在 0~360 均匀分布，跳过黄色系(45~65)避免刺眼
        val hueBase = (360f / count) * idx
        val hue = if (hueBase in 45f..65f) hueBase + 30f else hueBase
        val bg = hslToColor(hue, 0.45f, 0.72f)
        val fg = Color(0xFFFFFFFF)
        map[name] = bg to fg
        idx++
    }
    return map
}

/**
 * 根据单个课程名生成稳定的颜色（与颜色集合无关，同名同色、不同名大概率不同色）。
 * 用名称哈希映射到色相环，避免 v2.13 前"每个名字都算作唯一项 → hue 恒为 0 → 所有卡片同色"的问题。
 */
fun getCourseColorByName(courseName: String): Pair<Color, Color> {
    val raw = courseName.hashCode().toLong() and 0xFFFFFFFFL
    var hue = (raw % 360L).toFloat()
    if (hue in 45f..65f) hue = (hue + 40f) % 360f
    return hslToColor(hue, 0.45f, 0.72f) to Color(0xFFFFFFFF)
}

/** HSL → Compose Color，h∈[0,360), s/l∈[0,1] */
private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
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
