package com.example.courseschedule.ui.screen.week

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.importer.ImportedCourse
import com.example.courseschedule.data.importer.XlsxCourseParser
import com.example.courseschedule.ui.screen.week.WeekViewModel.ImportConflict
import com.example.courseschedule.ui.component.AddCourseDialog
import com.example.courseschedule.ui.component.CourseScheduleTopBar
import com.example.courseschedule.ui.component.EditCourseDialog
import com.example.courseschedule.ui.component.SemesterSetupDialog
import com.example.courseschedule.ui.component.WeekGrid
import com.example.courseschedule.ui.navigation.NavigationState
import com.example.courseschedule.util.DateUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
fun WeekScreen(
    onCourseClick: (Long) -> Unit,
    viewModel: WeekViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Observe NavigationState changes (set by calendar click)
    val navTargetWeek = NavigationState.targetWeek
    val navTargetDay = NavigationState.targetDayOfWeek
    LaunchedEffect(navTargetWeek, navTargetDay) {
        viewModel.consumeTargetWeek()
    }

    // Auto-clear highlight after 2 seconds
    LaunchedEffect(state.highlightDayOfWeek) {
        if (state.highlightDayOfWeek > 0) {
            kotlinx.coroutines.delay(2000L)
            viewModel.clearHighlight()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSemesterDialog by remember { mutableStateOf(false) }
    var longPressDay by remember { mutableIntStateOf(1) }
    var longPressPeriod by remember { mutableIntStateOf(1) }
    var editCourse by remember { mutableStateOf<Course?>(null) }
    var editSchedule by remember { mutableStateOf<Schedule?>(null) }
    var addDialogConflicts by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }

    // 导入课表状态
    var importResult by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var parsedCourses by remember { mutableStateOf<List<ImportedCourse>>(emptyList()) }
    var importConflicts by remember { mutableStateOf<List<ImportConflict>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val courses = context.contentResolver.openInputStream(uri)?.use { XlsxCourseParser.parse(it) }
                        ?: emptyList()
                    if (courses.isEmpty()) {
                        importError = "\u672a\u89e3\u6790\u51fa\u6709\u6548\u8bfe\u7a0b\uff08\u8bf7\u786e\u8ba4\u8868\u683c\u5305\u542b\u201c\u8bfe\u7a0b\u540d\u79f0\u201d\u4e0e\u201c\u4e0a\u8bfe\u65f6\u95f4\u201d\u5217\uff09"
                    } else {
                        parsedCourses = courses
                        importConflicts = viewModel.checkImportConflicts(courses)
                        showImportConfirm = true
                    }
                } catch (e: Exception) {
                    importError = "\u5bfc\u5165\u5931\u8d25\uff1a${e.message}"
                }
            }
        }
    }

    // 导入结果确认
    if (showImportConfirm && parsedCourses.isNotEmpty()) {
        val conflictCount = importConflicts.size
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("\u5bfc\u5165\u8bfe\u7a0b\u786e\u8ba4") },
            text = {
                Column {
                    Text(
                        "\u53d1\u73b0 ${parsedCourses.size} \u95e8\u8bfe\u7a0b\uff0c\u5c06\u5bfc\u5165\u5f53\u524d\u5b66\u671f\uff1a",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    parsedCourses.take(8).forEach {
                        Text("\u2022 ${it.name}", fontSize = 13.sp, maxLines = 1)
                    }
                    if (parsedCourses.size > 8) {
                        Text("...\u7b49${parsedCourses.size}\u95e8", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (conflictCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "\u26a0 \u4e0e\u5df2\u6709\u8bfe\u7a0b\u65f6\u95f4\u51b2\u7a81\u7684\u65f6\u6bb5 ${conflictCount} \u4e2a\uff1a",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                        val dayNames = listOf("\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94", "\u5468\u516d", "\u5468\u65e5")
                        importConflicts.take(6).forEach {
                            Text(
                                "\u2022 ${it.importedCourseName} \u4e0e ${it.existingName} \uff08${dayNames.getOrElse(it.dayOfWeek - 1) { "?" }}\u7b2c${it.startPeriod}-${it.endPeriod}\u8282\uff09",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1
                            )
                        }
                        if (conflictCount > 6) {
                            Text("...\u7b49${conflictCount}\u4e2a\u51b2\u7a81", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    val toImport = parsedCourses
                    parsedCourses = emptyList()
                    importConflicts = emptyList()
                    scope.launch {
                        try {
                            val (courses, schedules) = viewModel.importParsedCourses(toImport)
                            importResult = courses to schedules
                        } catch (e: Exception) {
                            importError = "\u5bfc\u5165\u5931\u8d25\uff1a${e.message}"
                        }
                    }
                }) { Text("\u5168\u90e8\u5bfc\u5165") }
            },
            dismissButton = {
                Row {
                    if (conflictCount > 0) {
                        TextButton(onClick = {
                            showImportConfirm = false
                            val conflictNames = importConflicts.map { it.importedCourseName }.toSet()
                            val toImport = parsedCourses.filter { it.name !in conflictNames }
                            parsedCourses = emptyList()
                            importConflicts = emptyList()
                            scope.launch {
                                try {
                                    val (courses, schedules) = viewModel.importParsedCourses(toImport)
                                    importResult = courses to schedules
                                } catch (e: Exception) {
                                    importError = "\u5bfc\u5165\u5931\u8d25\uff1a${e.message}"
                                }
                            }
                        }) {
                            Text("\u8df3\u8fc7\u51b2\u7a81\u5bfc\u5165", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    TextButton(onClick = { showImportConfirm = false; parsedCourses = emptyList(); importConflicts = emptyList() }) { Text("\u53d6\u6d88") }
                }
            }
        )
    }
    importResult?.let { (courses, schedules) ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("\u5bfc\u5165\u5b8c\u6210") },
            text = { Text("\u6210\u529f\u5bfc\u5165 ${courses} \u95e8\u8bfe\u7a0b\u3001${schedules} \u4e2a\u6392\u8bfe\u65f6\u6bb5\u3002") },
            confirmButton = {
                TextButton(onClick = { importResult = null }) { Text("\u597d") }
            }
        )
    }
    importError?.let { msg ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("\u5bfc\u5165\u5931\u8d25") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { importError = null }) { Text("\u597d") }
            }
        )
    }

    // 每周一页的官方 Pager：拖动时相邻两周始终并排可见，彻底消除单页滑动方案的空背景黑屏
    val pagerState = rememberPagerState(
        initialPage = (state.selectedWeek - 1).coerceAtLeast(0)
    ) { state.totalWeeks.coerceAtLeast(1) }
    // 首次真实学期数据就位前不做跳页动画，避免首屏横跳
    var pagerReady by remember { mutableStateOf(false) }

    // selectedWeek（VM）是唯一事实源：日历跳转/"本周"/周导航按钮经 selectWeek 驱动 Pager 滚动
    var suppressPagerSync by remember { mutableStateOf(false) }
    LaunchedEffect(state.selectedWeek, state.totalWeeks, state.semester) {
        if (state.semester == null || state.totalWeeks < 1) return@LaunchedEffect
        val target = (state.selectedWeek - 1).coerceIn(0, state.totalWeeks - 1)
        if (pagerState.currentPage != target) {
            suppressPagerSync = true
            try {
                // 首次数据就位用 scrollToPage 直接跳（无动画），之后外部跳转走动画
                if (pagerReady) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
            } finally {
                suppressPagerSync = false
            }
        }
        pagerReady = true
    }

    // 手势/惯性翻页越过页中点后写回 VM；程序性滚动期间抑制，避免顶部周次被中间页刷掉
    LaunchedEffect(pagerState.currentPage) {
        if (!suppressPagerSync && state.semester != null) {
            viewModel.selectWeek(pagerState.currentPage + 1)
        }
    }

    fun switchToWeek(target: Int) {
        val clamped = target.coerceIn(1, state.totalWeeks)
        if (clamped != state.selectedWeek) viewModel.selectWeek(clamped)
    }

    Scaffold(
        topBar = {
            CourseScheduleTopBar(
                selectedWeek = state.selectedWeek,
                currentWeek = state.currentWeek,
                onSettingsClick = { showSemesterDialog = true },
                onBackToCurrentWeek = { switchToWeek(state.currentWeek) }
            )
        },
        content = { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                WeekNavigationBar(
                    selectedWeek = state.selectedWeek,
                    currentWeek = state.currentWeek,
                    totalWeeks = state.totalWeeks,
                    weekRange = state.currentPage.weekRange,
                    onPrevWeek = { switchToWeek(state.selectedWeek - 1) },
                    onNextWeek = { switchToWeek(state.selectedWeek + 1) },
                    onBackToCurrent = { switchToWeek(state.currentWeek) }
                )

                Box(modifier = Modifier.weight(1f)) {
                    // 各周页共享同一竖向滚动位置：横向换页不打断阅读进度
                    val weekScroll = rememberScrollState()
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1
                    ) { pageIndex ->
                        val week = pageIndex + 1
                        // 每页只渲染该周生效的时段（与 ViewModel.buildWeekPage 同一过滤规则）
                        val active = remember(week, state.allSchedules) {
                            state.allSchedules.filter {
                                DateUtils.isScheduleActive(it.startWeek, it.endWeek, it.weekType, week)
                            }
                        }
                        val courses = remember(week, active, state.allCourseMap) {
                            active.mapNotNull { state.allCourseMap[it.courseId] }.associateBy { it.id }
                        }
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(weekScroll)
                        ) {
                            WeekGrid(
                                schedules = active,
                                courses = courses,
                                roomMap = state.currentPage.roomMap,
                                semester = state.semester,
                                currentDayOfWeek = state.currentDayOfWeek,
                                highlightDayOfWeek = if (state.highlightDayOfWeek > 0) state.highlightDayOfWeek
                                    else if (week == state.currentWeek) state.currentDayOfWeek else 0,
                                selectedWeek = week,
                                modifier = Modifier.fillMaxWidth(),
                                onCellClick = { course, _ -> onCourseClick(course.id) },
                                onCellLongClick = { day, period ->
                                    longPressDay = day; longPressPeriod = period
                                    scope.launch {
                                        val conflicts = viewModel.checkConflict(
                                            dayOfWeek = day, startPeriod = period, endPeriod = period,
                                            weekType = 0, startWeek = 1, endWeek = state.totalWeeks
                                        )
                                        addDialogConflicts = conflicts.map { it.courseName to it.dayOfWeek }
                                    }
                                    showAddDialog = true
                                },
                                onCourseLongClick = { course, schedule ->
                                    editCourse = course; editSchedule = schedule; showEditDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    )

    if (showAddDialog) {
        AddCourseDialog(
            dayOfWeek = longPressDay, period = longPressPeriod,
            currentWeek = state.selectedWeek, totalWeeks = state.totalWeeks,
            periodCount = state.semester?.periodCount ?: 12,
            weekDays = state.semester?.weekDays ?: 5,
            conflicts = addDialogConflicts,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, teacher, room, weekType, startWeek, endWeek, startPeriod, endPeriod ->
                viewModel.addCourse(longPressDay, name, teacher, room, weekType, startWeek, endWeek, startPeriod, endPeriod)
                showAddDialog = false
            }
        )
    }

    val curEditCourse = editCourse
    val curEditSchedule = editSchedule
    if (showEditDialog && curEditCourse != null && curEditSchedule != null) {
        val course = curEditCourse
        val sched = curEditSchedule
        EditCourseDialog(
            courseName = course.name,
            courseTeacher = course.teacher,
            courseRoom = sched.roomId?.let { state.currentPage.roomMap[it] } ?: "",
            dayOfWeek = sched.dayOfWeek,
            startPeriod = sched.startPeriod,
            endPeriod = sched.endPeriod,
            startWeek = sched.startWeek,
            endWeek = sched.endWeek,
            weekType = sched.weekType,
            totalWeeks = state.totalWeeks,
            periodCount = state.semester?.periodCount ?: 12,
            weekDays = state.semester?.weekDays ?: 5,
            onDismiss = { showEditDialog = false; editCourse = null; editSchedule = null },
            onConfirm = { name, teacher, room, dayOfWeek, weekType, startWeek, endWeek, startPeriod, endPeriod ->
                viewModel.updateCourseAndSchedule(
                    courseId = course.id, scheduleId = sched.id,
                    name = name, teacher = teacher, room = room, dayOfWeek = dayOfWeek, weekType = weekType,
                    startWeek = startWeek, endWeek = endWeek, startPeriod = startPeriod, endPeriod = endPeriod
                )
                showEditDialog = false; editCourse = null; editSchedule = null
            },
            onDelete = {
                viewModel.deleteSchedule(sched.id)
                showEditDialog = false; editCourse = null; editSchedule = null
            }
        )
    }
    if (showSemesterDialog) {
        SemesterSetupDialog(
            semester = state.semester,
            savedPresets = state.presets.filter { it.id != state.semester?.id },
            maxScheduledPeriod = state.maxScheduledPeriod,
            hasWeekendCourses = state.hasWeekendCourses,
            onDismiss = { showSemesterDialog = false },
            onConfirm = { name, startDate, totalWeeks, periodCount, weekDays, periodTimesJson ->
                viewModel.saveSemester(name, startDate, totalWeeks, periodCount, weekDays, periodTimesJson)
                showSemesterDialog = false
            },
            onLoadPreset = { preset ->
                viewModel.saveSemester(preset.name, preset.startDate, preset.totalWeeks, preset.periodCount, preset.weekDays, preset.periodTimesJson)
            },
            onDeletePreset = { viewModel.deletePreset(it) },
            onImportClick = {
                showSemesterDialog = false
                importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream", "*/*"))
            }
        )
    }
}

// ── WeekNavigationBar ────────────────────────────────────────────────

@Composable
private fun WeekNavigationBar(
    selectedWeek: Int,
    currentWeek: Int,
    totalWeeks: Int,
    weekRange: String,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onBackToCurrent: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPrevWeek,
                enabled = selectedWeek > 1,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            if (weekRange.isNotEmpty()) {
                Text(weekRange, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = onNextWeek,
                enabled = selectedWeek < totalWeeks,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        if (selectedWeek != currentWeek) {
            FilledTonalButton(
                onClick = onBackToCurrent,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(26.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(11.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("\u672c\u5468", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
