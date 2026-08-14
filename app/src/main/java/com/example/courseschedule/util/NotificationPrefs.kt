package com.example.courseschedule.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NotificationPrefs {
    private const val PREFS_NAME = "settings"
    private const val KEY_ENABLED = "notifications_enabled"
    private lateinit var prefs: SharedPreferences
    private val _enabled = MutableStateFlow(true)

    val enabled: StateFlow<Boolean> = _enabled

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(KEY_ENABLED, true)
    }

    fun isEnabled(): Boolean = _enabled.value

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
