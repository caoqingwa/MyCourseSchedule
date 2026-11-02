package com.example.courseschedule.ui.screen.week

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.ui.component.AddCourseDialog
import com.example.courseschedule.ui.component.CourseScheduleTopBar
import com.example.courseschedule.ui.component.EditCourseDialog
import com.example.courseschedule.ui.component.WeekGrid
import com.example.courseschedule.ui.navigation.NavigationState
import com.example.courseschedule.util.DateUtils
import com.example.courseschedule.util.weeksForSpan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
fun WeekScreen(
    onCourseClick: (courseId: Long, scheduleId: Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    autoAddCourse: Boolean = false,
    onAutoAddConsumed: () -> Unit = {},
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
    var longPressDay by remember { mutableIntStateOf(1) }
    var longPressPeriod by remember { mutableIntStateOf(1) }
    var editCourse by remember { mutableStateOf<Course?>(null) }
    var editSchedule by remember { mutableStateOf<Schedule?>(null) }

    /** 打开「添加课程」：优先用传入的星期/节次，其次用今天/当前节次，保证 FAB 也有合理默认值 */
    fun openAddDialogFor(day: Int = 0, period: Int = 0) {
        val weekDays = state.semester?.weekDays ?: 5
        val periodCount = state.semester?.periodCount ?: 12
        val today = state.currentDayOfWeek
        longPressDay = when {
            day in 1..weekDays -> day
            today in 1..weekDays -> today
            else -> 1
        }
        val nowPeriod = DateUtils.getCurrentPeriod(state.semester)
        longPressPeriod = when {
            period in 1..periodCount -> period
            nowPeriod in 1..periodCount -> nowPeriod
            else -> 1
        }
        showAddDialog = true
    }

    // 今日页的「添加课程」按钮：跳到这里后直接弹窗
    LaunchedEffect(autoAddCourse, state.semester) {
        if (autoAddCourse && state.semester != null) {
            onAutoAddConsumed()
            openAddDialogFor()
        }
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

    // 手势/惯性翻页越过页中点后写回 VM；程序性滚动期间抑制，避免顶部周次被中间页刷掉。
    // 跳过首次组合的初始页回调：外层 Pager 只保留当前页后周页面每次进入都会重建，
    // 首次回调用的是基于旧 selectedWeek 的初始页，会把日历跳转刚设好的目标周覆盖掉。
    var pagerWriteBackReady by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        if (!pagerWriteBackReady) {
            pagerWriteBackReady = true
            return@LaunchedEffect
        }
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
                onSettingsClick = onOpenSettings,
                onBackToCurrentWeek = { switchToWeek(state.currentWeek) }
            )
        },
        floatingActionButton = {
            if (state.semester != null) {
                FloatingActionButton(
                    onClick = { openAddDialogFor() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "\u6dfb\u52a0\u8bfe\u7a0b")
                }
            }
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
                    // 各周页共享同一竖向滚动位置；仅当前页响应竖向手势，避免同一 ScrollState 挂多个滚动容器
                    val weekScroll = rememberScrollState()
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0
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
                            modifier = Modifier.fillMaxSize()
                                .verticalScroll(weekScroll, enabled = pageIndex == pagerState.currentPage)
                        ) {
                            WeekGrid(
                                schedules = active,
                                courses = courses,
                                roomMap = state.currentPage.roomMap,
                                semester = state.semester,
                                // 只有当前显示的周才可能高亮：别的周传 0，交由 WeekGrid 完全不画高亮
                                highlightDayOfWeek = when {
                                    pageIndex != pagerState.currentPage -> 0
                                    state.highlightDayOfWeek > 0 -> state.highlightDayOfWeek
                                    week == state.currentWeek -> state.currentDayOfWeek
                                    else -> 0
                                },
                                showNowIndicator = week == state.currentWeek,
                                selectedWeek = week,
                                modifier = Modifier.fillMaxWidth(),
                                onCellClick = { course, schedule -> onCourseClick(course.id, schedule.id) },
                                onCellLongClick = { day, period -> openAddDialogFor(day, period) },
                                onCourseLongClick = { course, schedule ->
                                    editCourse = course; editSchedule = schedule; showEditDialog = true
                                }
                            )
                            if (active.isEmpty()) {
                                Text(
                                    "\u957f\u6309\u7a7a\u767d\u683c\u5b50\u6dfb\u52a0\u8bfe\u7a0b\uff0c\u4e5f\u53ef\u4ee5\u70b9\u53f3\u4e0b\u89d2\u7684 +",
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
            checkConflicts = { day, weeks, startPeriod, endPeriod ->
                viewModel.checkConflict(
                    dayOfWeek = day, startPeriod = startPeriod,
                    endPeriod = endPeriod, weeks = weeks
                ).map { it.courseName to it.period }
            },
            onDismiss = { showAddDialog = false },
            onConfirm = { name, teacher, room, day, weeks, startPeriod, endPeriod ->
                viewModel.addCourse(day, name, teacher, room, weeks, startPeriod, endPeriod)
                showAddDialog = false
            }
        )
    }

    val curEditCourse = editCourse
    val curEditSchedule = editSchedule
    if (showEditDialog && curEditCourse != null && curEditSchedule != null) {
        val course = curEditCourse
        val sched = curEditSchedule
        val groupSchedules = state.allSchedules.filter {
            it.courseId == course.id && it.dayOfWeek == sched.dayOfWeek &&
                it.startPeriod == sched.startPeriod && it.endPeriod == sched.endPeriod &&
                it.roomId == sched.roomId
        }
        val groupWeeks = groupSchedules.flatMap { weeksForSpan(it.startWeek, it.endWeek, it.weekType) }.toSet()
        val initialWeeks = if (groupWeeks.isEmpty()) {
            weeksForSpan(sched.startWeek, sched.endWeek, sched.weekType)
        } else groupWeeks
        val excludedIds = groupSchedules.map { it.id }.toSet().ifEmpty { setOf(sched.id) }
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
            initialWeeks = initialWeeks,
            checkConflicts = { weeks, startPeriod, endPeriod ->
                viewModel.checkConflict(
                    dayOfWeek = sched.dayOfWeek, startPeriod = startPeriod,
                    endPeriod = endPeriod, weeks = weeks, excludeScheduleIds = excludedIds
                ).map { it.courseName to it.period }
            },
            onDismiss = { showEditDialog = false; editCourse = null; editSchedule = null },
            onConfirm = { name, teacher, room, dayOfWeek, weeks, startPeriod, endPeriod ->
                viewModel.updateCourseAndSchedule(
                    courseId = course.id, scheduleId = sched.id,
                    name = name, teacher = teacher, room = room, dayOfWeek = dayOfWeek, weeks = weeks,
                    startPeriod = startPeriod, endPeriod = endPeriod
                )
                showEditDialog = false; editCourse = null; editSchedule = null
            },
            onDelete = {
                viewModel.deleteSchedule(sched.id)
                showEditDialog = false; editCourse = null; editSchedule = null
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
