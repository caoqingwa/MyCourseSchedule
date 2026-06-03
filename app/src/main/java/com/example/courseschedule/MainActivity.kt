package com.example.courseschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.courseschedule.ui.navigation.Screen
import com.example.courseschedule.ui.navigation.bottomNavItems
import com.example.courseschedule.ui.component.BottomNavBar
import com.example.courseschedule.ui.screen.today.TodayScreen
import com.example.courseschedule.ui.screen.week.WeekScreen
import com.example.courseschedule.ui.screen.calendar.CalendarScreen
import com.example.courseschedule.ui.theme.CourseScheduleTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CourseScheduleTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            BottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    navController.navigate(screen.route) {
                        popUpTo(Screen.Today.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                screens = bottomNavItems
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Today.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Today.route) {
                TodayScreen(onCourseClick = { /* navigate to detail */ })
            }
            composable(Screen.Week.route) {
                WeekScreen(onCourseClick = { /* navigate to detail */ })
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    onDayClick = { navController.navigate(Screen.Today.route) },
                    onNavigateToToday = { navController.navigate(Screen.Today.route) }
                )
            }
        }
    }
}
