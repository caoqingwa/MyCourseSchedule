package com.example.courseschedule.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Today : Screen("today", "\u4eca\u65e5", Icons.Default.Today)
    object Week : Screen("week", "\u5468\u8bfe\u8868", Icons.Default.DateRange)
    object Calendar : Screen("calendar", "\u65e5\u5386", Icons.Default.CalendarMonth)
}

val bottomNavItems = listOf(Screen.Today, Screen.Week, Screen.Calendar)