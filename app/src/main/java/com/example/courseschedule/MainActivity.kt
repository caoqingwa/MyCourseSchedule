package com.example.courseschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import com.example.courseschedule.ui.navigation.Screen
import com.example.courseschedule.ui.navigation.bottomNavItems
import com.example.courseschedule.ui.navigation.NavigationState
import com.example.courseschedule.ui.component.BottomNavBar
import com.example.courseschedule.ui.screen.today.TodayScreen
import com.example.courseschedule.ui.screen.week.WeekScreen
import com.example.courseschedule.ui.screen.calendar.CalendarScreen
import com.example.courseschedule.ui.theme.CourseScheduleTheme
import com.example.courseschedule.util.DateUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val screens = bottomNavItems

    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(1f) }
    var transitioning by remember { mutableStateOf(false) }

    val pageSpring = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow
    )

    val navigateTo = remember(pagerState, transitioning, alpha, scale, pageSpring) {
        { index: Int ->
            val current = pagerState.currentPage
            if (index != current && !transitioning) {
                coroutineScope.launch {
                    if (kotlin.math.abs(index - current) > 1) {
                        transitioning = true
                        coroutineScope {
                            launch { alpha.animateTo(0f, pageSpring) }
                            launch { scale.animateTo(0.92f, pageSpring) }
                        }
                        pagerState.scrollToPage(index)
                        alpha.snapTo(0f)
                        scale.snapTo(1.08f)
                        coroutineScope {
                            launch { alpha.animateTo(1f, pageSpring) }
                            launch { scale.animateTo(1f, pageSpring) }
                        }
                        transitioning = false
                    } else {
                        pagerState.animateScrollToPage(index)
                    }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                currentRoute = screens[pagerState.currentPage].route,
                onNavigate = { screen ->
                    val targetIndex = screens.indexOf(screen)
                    if (targetIndex >= 0) navigateTo(targetIndex)
                },
                screens = screens
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .graphicsLayer {
                    this.alpha = alpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                },
            userScrollEnabled = !transitioning,
            beyondViewportPageCount = 0
        ) { page ->
            when (page) {
                0 -> TodayScreen(onCourseClick = { })
                1 -> WeekScreen(onCourseClick = { })
                2 -> CalendarScreen(
                    onDayClick = { dayMillis, weekNumber ->
                        NavigationState.targetWeek = weekNumber
                        NavigationState.targetDayOfWeek = DateUtils.getDayOfWeek(dayMillis)
                        navigateTo(1)
                    },
                    onNavigateToToday = { navigateTo(0) }
                )
            }
        }
    }
}