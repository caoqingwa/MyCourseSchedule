package com.example.courseschedule.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 全局设置持久化：字体缩放比例等 */
object SettingsPrefs {
    private const val PREFS_NAME = "settings"
    private const val KEY_FONT_SCALE = "font_scale"
    private lateinit var prefs: SharedPreferences
    private val _fontScale = MutableStateFlow(1.0f)

    val fontScale: StateFlow<Float> = _fontScale

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _fontScale.value = prefs.getFloat(KEY_FONT_SCALE, 1.0f).coerceIn(0.8f, 1.4f)
    }

    fun getFontScale(): Float = _fontScale.value

    fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.8f, 1.4f)
        _fontScale.value = clamped
        prefs.edit().putFloat(KEY_FONT_SCALE, clamped).apply()
    }
}
