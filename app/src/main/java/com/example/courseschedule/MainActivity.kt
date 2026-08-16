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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.BuildConfig
import com.example.courseschedule.ui.navigation.Screen
import com.example.courseschedule.ui.navigation.bottomNavItems
import com.example.courseschedule.ui.navigation.NavigationState
import com.example.courseschedule.ui.component.BottomNavBar
import com.example.courseschedule.ui.component.EditCourseDialog
import com.example.courseschedule.ui.component.SemesterSetupDialog
import com.example.courseschedule.ui.component.SettingsScreen
import com.example.courseschedule.ui.screen.detail.CourseDetailSheet
import com.example.courseschedule.ui.screen.detail.CourseDetailViewModel
import com.example.courseschedule.ui.screen.today.TodayScreen
import com.example.courseschedule.ui.screen.week.WeekScreen
import com.example.courseschedule.ui.screen.week.WeekViewModel
import com.example.courseschedule.ui.screen.calendar.CalendarScreen
import com.example.courseschedule.ui.theme.CourseScheduleTheme
import com.example.courseschedule.util.DateUtils
import com.example.courseschedule.util.SettingsPrefs
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

    var selectedCourseId by remember { mutableStateOf<Long?>(null) }
    val detailViewModel: CourseDetailViewModel = hiltViewModel()
    val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val weekViewModel: WeekViewModel = hiltViewModel()

    var showSettings by remember { mutableStateOf(false) }
    var showSettingsSemesterDialog by remember { mutableStateOf(false) }
    val fontScale by SettingsPrefs.fontScale.collectAsStateWithLifecycle()

    var editTarget by remember { mutableStateOf<Pair<Course, Schedule>?>(null) }
    var editRoom by remember { mutableStateOf("") }
    var editTotalWeeks by remember { mutableIntStateOf(20) }
    var editPeriodCount by remember { mutableIntStateOf(12) }
    var editWeekDays by remember { mutableIntStateOf(5) }

    LaunchedEffect(selectedCourseId) {
        selectedCourseId?.let { detailViewModel.show(it) }
    }

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
                0 -> TodayScreen(onCourseClick = { selectedCourseId = it })
                1 -> WeekScreen(onCourseClick = { selectedCourseId = it })
                2 -> CalendarScreen(
                    onDayClick = { dayMillis, weekNumber ->
                        NavigationState.targetWeek = weekNumber
                        NavigationState.targetDayOfWeek = DateUtils.getDayOfWeek(dayMillis)
                        navigateTo(1)
                    },
                    onNavigateToToday = { navigateTo(0) },
                    onOpenSettings = { showSettings = true }
                )
            }
        }
    }

    detailState.course?.let { course ->
        CourseDetailSheet(
            course = course,
            schedules = detailState.schedules,
            roomName = detailState.roomName,
            onDismiss = {
                selectedCourseId = null
                detailViewModel.clear()
            },
            onEdit = {
                detailState.schedules.firstOrNull()?.let { sched ->
                    editTarget = course to sched
                    editRoom = detailState.roomName ?: ""
                    detailState.semester?.let {
                        editTotalWeeks = it.totalWeeks
                        editPeriodCount = it.periodCount
                        editWeekDays = it.weekDays
                    }
                }
                selectedCourseId = null
                detailViewModel.clear()
            }
        )
    }

    val target = editTarget
    val targetCourse = target?.first
    val targetSchedule = target?.second
    if (targetCourse != null && targetSchedule != null) {
        EditCourseDialog(
            courseName = targetCourse.name,
            courseTeacher = targetCourse.teacher,
            courseRoom = editRoom,
            dayOfWeek = targetSchedule.dayOfWeek,
            startPeriod = targetSchedule.startPeriod,
            endPeriod = targetSchedule.endPeriod,
            startWeek = targetSchedule.startWeek,
            endWeek = targetSchedule.endWeek,
            weekType = targetSchedule.weekType,
            totalWeeks = editTotalWeeks,
            periodCount = editPeriodCount,
            weekDays = editWeekDays,
            onDismiss = { editTarget = null },
            onConfirm = { name, teacher, room, dayOfWeek, weekType, startWeek, endWeek, startPeriod, endPeriod ->
                weekViewModel.updateCourseAndSchedule(
                    courseId = targetCourse.id, scheduleId = targetSchedule.id,
                    name = name, teacher = teacher, room = room, dayOfWeek = dayOfWeek, weekType = weekType,
                    startWeek = startWeek, endWeek = endWeek, startPeriod = startPeriod, endPeriod = endPeriod
                )
                editTarget = null
            },
            onDelete = {
                weekViewModel.deleteCourse(targetCourse.id)
                editTarget = null
            }
        )
    }

    if (showSettings) {
        // 全屏覆盖设置页（字体缩放随 SettingsPrefs 实时变化）
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = density.density,
                fontScale = density.fontScale * fontScale
            )
        ) {
            SettingsScreen(
                versionName = BuildConfig.VERSION_NAME,
                onBack = { showSettings = false },
                onOpenSemesterSetup = { showSettingsSemesterDialog = true },
                onClearAll = {
                    weekViewModel.clearAllCourseData()
                    showSettings = false
                }
            )
        }
    }
    if (showSettingsSemesterDialog) {
        val settingsState = weekViewModel.uiState.collectAsStateWithLifecycle().value
        SemesterSetupDialog(
            semester = settingsState.semester,
            savedPresets = settingsState.presets.filter { it.id != settingsState.semester?.id },
            maxScheduledPeriod = settingsState.maxScheduledPeriod,
            hasWeekendCourses = settingsState.hasWeekendCourses,
            onDismiss = { showSettingsSemesterDialog = false },
            onConfirm = { name, startDate, totalWeeks, periodCount, weekDays, periodTimesJson ->
                weekViewModel.saveSemester(name, startDate, totalWeeks, periodCount, weekDays, periodTimesJson)
                showSettingsSemesterDialog = false
            },
            onLoadPreset = { preset ->
                weekViewModel.saveSemester(preset.name, preset.startDate, preset.totalWeeks, preset.periodCount, preset.weekDays, preset.periodTimesJson)
            },
            onDeletePreset = { weekViewModel.deletePreset(it) },
            onImportClick = null
        )
    }
}