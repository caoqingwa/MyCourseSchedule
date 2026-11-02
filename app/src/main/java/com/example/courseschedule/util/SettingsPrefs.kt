package com.example.courseschedule.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 全局设置持久化：字体缩放比例、主题模式、动态取色 */
object SettingsPrefs {
    private const val PREFS_NAME = "settings"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    /** 跟随系统 */
    const val THEME_SYSTEM = 0
    /** 始终浅色 */
    const val THEME_LIGHT = 1
    /** 始终深色 */
    const val THEME_DARK = 2

    private lateinit var prefs: SharedPreferences
    private val _fontScale = MutableStateFlow(1.0f)
    private val _themeMode = MutableStateFlow(THEME_SYSTEM)
    // 默认关闭：保留 App 自己的品牌绿，避免被壁纸色顶掉
    private val _dynamicColor = MutableStateFlow(false)

    val fontScale: StateFlow<Float> = _fontScale
    val themeMode: StateFlow<Int> = _themeMode
    val dynamicColor: StateFlow<Boolean> = _dynamicColor

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _fontScale.value = prefs.getFloat(KEY_FONT_SCALE, 1.0f).coerceIn(0.8f, 1.4f)
        _themeMode.value = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM).coerceIn(THEME_SYSTEM, THEME_DARK)
        _dynamicColor.value = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
    }

    fun getFontScale(): Float = _fontScale.value

    fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.8f, 1.4f)
        _fontScale.value = clamped
        prefs.edit().putFloat(KEY_FONT_SCALE, clamped).apply()
    }

    fun getThemeMode(): Int = _themeMode.value

    fun setThemeMode(mode: Int) {
        val clamped = mode.coerceIn(THEME_SYSTEM, THEME_DARK)
        _themeMode.value = clamped
        prefs.edit().putInt(KEY_THEME_MODE, clamped).apply()
    }

    fun getDynamicColor(): Boolean = _dynamicColor.value

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }
}
