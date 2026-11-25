package com.example.courseschedule

import android.graphics.Color as AndroidColor
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.db.entity.ScheduleChange
import com.example.courseschedule.ui.component.ScheduleChangeDialog
import com.example.courseschedule.util.weeksForSpan
import com.example.courseschedule.BuildConfig
import com.example.courseschedule.ui.navigation.Screen
import com.example.courseschedule.ui.navigation.bottomNavItems
import com.example.courseschedule.ui.navigation.NavigationState
import com.example.courseschedule.ui.component.BottomNavBar
import com.example.courseschedule.ui.component.CourseImportFlow
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
import com.example.courseschedule.util.NotificationHelper
import com.example.courseschedule.util.SettingsPrefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var notificationExamId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openNotification(intent)
        setContent {
            val themeMode by SettingsPrefs.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by SettingsPrefs.dynamicColor.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                SettingsPrefs.THEME_LIGHT -> false
                SettingsPrefs.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            // 手动选深色而系统是浅色时，状态栏/导航栏也要跟着换，否则图标看不见
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                    navigationBarStyle = if (darkTheme) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                )
            }
            CourseScheduleTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                MainApp(notificationExamId) {
                    notificationExamId = null
                    intent.removeExtra(NotificationHelper.EXTRA_EXAM_ID)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNotification(intent)
    }

    private fun openNotification(intent: Intent) {
        notificationExamId = intent.getLongExtra(NotificationHelper.EXTRA_EXAM_ID, -1L).takeIf { it > 0 }
    }
}

@Composable
fun MainApp(notificationExamId: Long? = null, onNotificationOpened: () -> Unit = {}) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val screens = bottomNavItems

    var selectedCourseId by remember { mutableStateOf<Long?>(null) }
    var selectedScheduleId by remember { mutableStateOf<Long?>(null) }
    var selectedOriginalDay by remember { mutableStateOf<Long?>(null) }
    var adjustment by remember { mutableStateOf<Pair<ScheduleChange, Boolean>?>(null) }
    val detailViewModel: CourseDetailViewModel = hiltViewModel()
    val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val weekViewModel: WeekViewModel = hiltViewModel()
    val adjustmentState by weekViewModel.uiState.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showSettingsSemesterDialog by remember { mutableStateOf(false) }
    var creatingSemester by remember { mutableStateOf(false) }
    var pendingAddCourse by remember { mutableStateOf(false) }
    var pendingExamId by rememberSaveable { mutableStateOf<Long?>(null) }
    var importRequest by remember { mutableIntStateOf(0) }
    val fontScale by SettingsPrefs.fontScale.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    var editTarget by remember { mutableStateOf<Pair<Course, Schedule>?>(null) }
    var editRoom by remember { mutableStateOf("") }
    var editWeeks by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var editGroupIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var editTotalWeeks by remember { mutableIntStateOf(20) }
    var editPeriodCount by remember { mutableIntStateOf(12) }
    var editWeekDays by remember { mutableIntStateOf(5) }

    LaunchedEffect(selectedCourseId) {
        selectedCourseId?.let { detailViewModel.show(it) }
    }

    var navigationJob by remember { mutableStateOf<Job?>(null) }
    val navigateTo: (Int) -> Unit = { index ->
        navigationJob?.cancel()
        navigationJob = coroutineScope.launch { pagerState.scrollToPage(index) }
    }
    LaunchedEffect(notificationExamId) {
        notificationExamId?.let {
            showSettings = false
            showSettingsSemesterDialog = false
            selectedCourseId = null
            detailViewModel.clear()
            adjustment = null
            editTarget = null
            pendingExamId = it
            navigateTo(2)
            onNotificationOpened()
        }
    }

    // 全局字体缩放：覆盖所有页面（今日/周课表/日历/设置/弹窗）
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * fontScale
        )
    ) {
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
                .fillMaxSize(),
            // 相邻页保持组合：来回切 Tab 时不必整页重建（周课表那页重建一次要组合上百个格子）
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> TodayScreen(
                    onImportCourse = { importRequest++ },
                    onExamClick = { examId ->
                        pendingExamId = examId
                        navigateTo(2)
                    },
                    onCourseClick = { courseId, scheduleId, originalDay ->
                        selectedOriginalDay = originalDay
                        selectedCourseId = courseId
                        selectedScheduleId = scheduleId
                    },
                    onOpenSettings = { showSettings = true },
                    onAddCourse = {
                        pendingAddCourse = true
                        navigateTo(1)
                    }
                )
                1 -> WeekScreen(
                    onOpenStoppedClass = { adjustment = it to true },
                    onCourseClick = { courseId, scheduleId, originalDay ->
                        selectedOriginalDay = originalDay
                        selectedCourseId = courseId
                        selectedScheduleId = scheduleId
                    },
                    onOpenSettings = { showSettings = true },
                    autoAddCourse = pendingAddCourse,
                    onAutoAddConsumed = { pendingAddCourse = false }
                )
                2 -> CalendarScreen(
                    pendingExamId = pendingExamId,
                    onPendingExamOpened = { pendingExamId = null },
                    onOpenWeek = { weekNumber, dayOfWeek ->
                        NavigationState.targetWeek = weekNumber
                        NavigationState.targetDayOfWeek = dayOfWeek
                        navigateTo(1)
                    },
                    onOpenSettings = { showSettings = true }
                )
            }
        }
    }

    detailState.course?.let { course ->
        val schedule = detailState.schedules.firstOrNull { it.id == selectedScheduleId }
        val originalDay = selectedOriginalDay
        CourseDetailSheet(
            course = course,
            schedules = detailState.schedules,
            scheduleRooms = detailState.scheduleRooms,
            roomSummary = detailState.roomSummary,
            onAdjustOccurrence = if (schedule != null && originalDay != null) ({
                val change = adjustmentState.scheduleChanges.firstOrNull {
                    it.scheduleId == schedule.id && it.originalDay == originalDay
                }
                adjustment = (change ?: ScheduleChange(
                    schedule.id, originalDay, originalDay, schedule.startPeriod, schedule.endPeriod,
                    detailState.scheduleRooms[schedule.id]
                )) to (change != null)
                selectedCourseId = null
                detailViewModel.clear()
            }) else null,
            onDismiss = {
                selectedCourseId = null
                selectedScheduleId = null
                detailViewModel.clear()
            },
            onEdit = {
                (detailState.schedules.firstOrNull { it.id == selectedScheduleId }
                    ?: detailState.schedules.firstOrNull())?.let { sched ->
                    editTarget = course to sched
                    editRoom = detailState.scheduleRooms[sched.id] ?: ""
                    val groupSchedules = detailState.schedules.filter {
                        it.dayOfWeek == sched.dayOfWeek && it.startPeriod == sched.startPeriod &&
                            it.endPeriod == sched.endPeriod && it.roomId == sched.roomId
                    }
                    val groupWeeks = groupSchedules.flatMap { weeksForSpan(it.startWeek, it.endWeek, it.weekType) }.toSet()
                    editWeeks = if (groupWeeks.isEmpty()) {
                        weeksForSpan(sched.startWeek, sched.endWeek, sched.weekType)
                    } else groupWeeks
                    editGroupIds = groupSchedules.map { it.id }.toSet().ifEmpty { setOf(sched.id) }
                    detailState.semester?.let {
                        editTotalWeeks = it.totalWeeks
                        editPeriodCount = it.periodCount
                        editWeekDays = it.weekDays
                    }
                }
                selectedCourseId = null
                selectedScheduleId = null
                detailViewModel.clear()
            }
        )
    }

    adjustment?.let { (change, existing) ->
        val semesterDays = adjustmentState.semester?.let { semester ->
            val start = Instant.ofEpochMilli(semester.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
            val monday = start.minusDays((start.dayOfWeek.value - 1).toLong())
            monday..monday.plusWeeks(semester.totalWeeks.toLong()).minusDays(1)
        }
        ScheduleChangeDialog(
            initial = change,
            existing = existing,
            semesterDays = semesterDays,
            onSave = weekViewModel::saveOccurrenceChange,
            onUndo = { weekViewModel.removeOccurrenceChange(change.scheduleId, change.originalDay) },
            onDismiss = { adjustment = null }
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
            initialWeeks = editWeeks,
            checkConflicts = { weeks, startPeriod, endPeriod ->
                weekViewModel.checkConflict(
                    dayOfWeek = targetSchedule.dayOfWeek, startPeriod = startPeriod,
                    endPeriod = endPeriod, weeks = weeks,
                    excludeScheduleIds = editGroupIds.ifEmpty { setOf(targetSchedule.id) }
                ).map { it.courseName to it.period }
            },
            onDismiss = { editTarget = null },
            onConfirm = { name, teacher, room, dayOfWeek, weeks, startPeriod, endPeriod ->
                weekViewModel.updateCourseAndSchedule(
                    courseId = targetCourse.id, scheduleId = targetSchedule.id,
                    name = name, teacher = teacher, room = room, dayOfWeek = dayOfWeek, weeks = weeks,
                    startPeriod = startPeriod, endPeriod = endPeriod
                )
                editTarget = null
            },
            onDelete = {
                weekViewModel.deleteSchedule(targetSchedule.id)
                editTarget = null
            }
        )
    }

    // 设置页为覆盖式全屏，系统返回键应回到上一页而不是直接退出应用
    BackHandler(enabled = showSettings) { showSettings = false }

    if (showSettings) {
        SettingsScreen(
            versionName = BuildConfig.VERSION_NAME,
            captureBackup = weekViewModel::captureBackup,
            restoreBackup = weekViewModel::restoreBackup,
            hasRestoreSnapshot = weekViewModel::hasRestoreSnapshot,
            undoRestore = weekViewModel::undoRestore,
            exportSemester = weekViewModel::exportSemester,
            onImport = { importRequest++ },
            onBack = { showSettings = false },
            onOpenSemesterSetup = {
                creatingSemester = false
                showSettingsSemesterDialog = true
            },
            onClearAll = {
                weekViewModel.clearAllCourseData()
                showSettings = false
            }
        )
    }
    if (showSettingsSemesterDialog) {
        val settingsState = weekViewModel.uiState.collectAsStateWithLifecycle().value
        key(settingsState.semester?.id, creatingSemester) {
        SemesterSetupDialog(
            semester = settingsState.semester,
            creating = creatingSemester,
            savedPresets = if (creatingSemester) emptyList() else settingsState.presets.filter { it.id != settingsState.semester?.id },
            maxScheduledPeriod = if (creatingSemester) 0 else settingsState.maxScheduledPeriod,
            hasWeekendCourses = !creatingSemester && settingsState.hasWeekendCourses,
            onCreateSemester = if (creatingSemester) null else ({ creatingSemester = true }),
            onDismiss = { showSettingsSemesterDialog = false },
            onConfirm = { name, startDate, totalWeeks, periodCount, weekDays, periodTimesJson ->
                if (creatingSemester) {
                    weekViewModel.createSemester(name, startDate, totalWeeks, periodCount, weekDays, periodTimesJson)
                } else {
                    weekViewModel.saveSemester(name, startDate, totalWeeks, periodCount, weekDays, periodTimesJson)
                }
                showSettingsSemesterDialog = false
            },
            onLoadPreset = { preset ->
                weekViewModel.selectSemester(preset)
                showSettingsSemesterDialog = false
            },
            onDeletePreset = { weekViewModel.deletePreset(it) },
            onImportClick = if (creatingSemester) null else ({ importRequest++ })
        )
        }
    }

    // xlsx 导入：以前藏在周课表齿轮里，现在全局设置的学期弹窗也能直接导入
    CourseImportFlow(
        requestKey = importRequest,
        onRequestHandled = { importRequest = 0 },
        previewImport = { weekViewModel.previewImport(it) },
        importCourses = weekViewModel::importParsedCourses
    )
    }
}
