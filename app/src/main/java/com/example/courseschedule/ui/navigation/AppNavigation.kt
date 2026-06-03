package com.example.courseschedule.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Today : Screen("today", "今日", Icons.Default.Today)
    object Week : Screen("week", "周课表", Icons.Default.DateRange)
    object Calendar : Screen("calendar", "日历", Icons.Default.CalendarMonth)
}

val bottomNavItems = listOf(Screen.Today, Screen.Week, Screen.Calendar)
