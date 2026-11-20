package com.example.courseschedule.ui.screen.week

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.courseschedule.util.SettingsPrefs
import com.example.courseschedule.util.scheduleOccurrences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.example.courseschedule.data.db.entity.ScheduleChange
import com.example.courseschedule.util.weeksForSpan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
fun WeekScreen(
    onCourseClick: (courseId: Long, scheduleId: Long, originalDay: Long) -> Unit,
    onOpenStoppedClass: (ScheduleChange) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    autoAddCourse: Boolean = false,
    onAutoAddConsumed: () -> Unit = {},
    viewModel: WeekViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listView by SettingsPrefs.weekListView.collectAsStateWithLifecycle()

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
                if (pagerReady && kotlin.math.abs(pagerState.currentPage - target) == 1) {
                    pagerState.animateScrollToPage(target)
                } else {
                    pagerState.scrollToPage(target)
                }
            } finally {
                suppressPagerSync = false
            }
        }
        pagerReady = true
    }

    // 等手势翻页停稳后再回写，避免拖过中点时导航逻辑抢占手势。
    // 跳过首次组合的初始页回调：外层 Pager 只保留当前页后周页面每次进入都会重建，
    // 首次回调用的是基于旧 selectedWeek 的初始页，会把日历跳转刚设好的目标周覆盖掉。
    var pagerWriteBackReady by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.settledPage) {
        if (!pagerWriteBackReady) {
            pagerWriteBackReady = true
            return@LaunchedEffect
        }
        if (!suppressPagerSync && state.semester != null) {
            viewModel.selectWeek(pagerState.settledPage + 1)
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
                        // 预组合左右相邻两周：否则拖动开始那一帧才去组合一整张 21 节课的网格，
                        // 这正是「一滑就掉帧」的主因
                        beyondViewportPageCount = 1
                    ) { pageIndex ->
                        val week = pageIndex + 1
                        val semester = state.semester
                        val start = remember(semester?.startDate) {
                            semester?.let { Instant.ofEpochMilli(it.startDate).atZone(ZoneId.systemDefault()).toLocalDate() }
                        }
                        val monday = start?.minusDays((start.dayOfWeek.value - 1).toLong())
                            ?.plusWeeks((week - 1).toLong())
                        val occurrences = remember(monday, semester, state.allSchedules, state.scheduleChanges) {
                            if (semester == null || start == null || monday == null) emptyList() else {
                                val first = monday.toEpochDay()
                                val days = first until first + 7
                                val changes = state.scheduleChanges.filter {
                                    it.originalDay in days || it.targetDay?.let(days::contains) == true
                                }
                                (0L..6L).flatMap { offset ->
                                    scheduleOccurrences(
                                        monday.plusDays(offset), start, semester.totalWeeks,
                                        state.allSchedules, changes
                                    )
                                }
                            }
                        }
                        val stopped = remember(monday, state.scheduleChanges) {
                            if (monday == null) emptyList() else {
                                val first = monday.toEpochDay()
                                state.scheduleChanges.filter { it.targetDay == null && it.originalDay in first until first + 7 }
                                    .sortedBy { it.originalDay }
                            }
                        }
                        val active = remember(occurrences) {
                            occurrences.mapIndexed { index, occurrence ->
                                if (occurrence.change == null) occurrence.schedule
                                else occurrence.schedule.copy(roomId = -1L - index)
                            }
                        }
                        val rooms = remember(occurrences, state.currentPage.roomMap) {
                            buildMap {
                                putAll(state.currentPage.roomMap)
                                occurrences.forEachIndexed { index, occurrence ->
                                    occurrence.change?.roomName?.let { put(-1L - index, it) }
                                }
                            }
                        }
                        val courses = remember(week, active, state.allCourseMap) {
                            active.mapNotNull { state.allCourseMap[it.courseId] }.associateBy { it.id }
                        }
                        if (listView) {
                            val grouped = remember(occurrences) { occurrences.groupBy { it.schedule.dayOfWeek } }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = pageIndex == pagerState.settledPage,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 84.dp)
                            ) {
                                grouped.forEach { (day, classes) ->
                                    item(key = "day_$day") {
                                        val date = monday?.plusDays((day - 1).toLong())
                                        Text(
                                            "${DateUtils.DAY_NAMES_7[day - 1]} · ${date?.monthValue}/${date?.dayOfMonth}",
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    items(classes, key = { "${it.schedule.id}:${it.originalDay}:${it.change != null}" }) { occurrence ->
                                        val schedule = occurrence.schedule
                                        val course = state.allCourseMap[schedule.courseId]
                                        if (course != null) {
                                            val room = if (occurrence.change != null) occurrence.change.roomName
                                                else schedule.roomId?.let { state.currentPage.roomMap[it] }
                                            val time = DateUtils.getPeriodTimeRange(schedule.startPeriod, schedule.endPeriod, semester)
                                            Card(
                                                onClick = { onCourseClick(course.id, schedule.id, occurrence.originalDay) },
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                            ) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text(course.name, style = MaterialTheme.typography.titleMedium)
                                                    Text("第 ${schedule.startPeriod}–${schedule.endPeriod} 节 · $time")
                                                    room?.takeIf { it.isNotBlank() }?.let { Text(it) }
                                                    if (occurrence.change != null) Text("调课")
                                                }
                                            }
                                        }
                                    }
                                }
                                items(stopped, key = { "stopped:${it.scheduleId}:${it.originalDay}" }) { entry ->
                                    val schedule = state.allSchedules.firstOrNull { it.id == entry.scheduleId }
                                    val name = schedule?.let { state.allCourseMap[it.courseId]?.name } ?: "课程"
                                    TextButton(onClick = { onOpenStoppedClass(entry) }) {
                                        Text("${LocalDate.ofEpochDay(entry.originalDay)} · $name 停课")
                                    }
                                }
                                if (occurrences.isEmpty() && stopped.isEmpty()) {
                                    item { Text("本周没有课程", modifier = Modifier.padding(vertical = 16.dp)) }
                                }
                            }
                        } else Column(
                            modifier = Modifier.fillMaxSize()
                                // 拖动时相邻周轻微缩小、淡出：滑动更有「翻页」层次感。
                                // 这里读 pagerState 的偏移量是写在 graphicsLayer 里由绘制阶段取值的，
                                // 不会每帧触发重组。
                                .graphicsLayer {
                                    val pageOffset =
                                        ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
                                            .coerceIn(-1f, 1f)
                                    val scale = 1f - 0.06f * kotlin.math.abs(pageOffset)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 1f - 0.25f * kotlin.math.abs(pageOffset)
                                }
                                .verticalScroll(weekScroll, enabled = pageIndex == pagerState.settledPage)
                        ) {
                            WeekGrid(
                                schedules = active,
                                courses = courses,
                                roomMap = rooms,
                                semester = state.semester,
                                showWeekend = occurrences.any { it.change != null && it.schedule.dayOfWeek > 5 },
                                // 只有当前显示的周才可能高亮：别的周传 0，交由 WeekGrid 完全不画高亮
                                highlightDayOfWeek = when {
                                    pageIndex != pagerState.settledPage -> 0
                                    state.highlightDayOfWeek > 0 -> state.highlightDayOfWeek
                                    week == state.currentWeek -> state.currentDayOfWeek
                                    else -> 0
                                },
                                showNowIndicator = week == state.currentWeek,
                                selectedWeek = week,
                                modifier = Modifier.fillMaxWidth(),
                                onCellClick = { course, schedule ->
                                    val index = active.indexOf(schedule)
                                    occurrences.getOrNull(index)?.let {
                                        onCourseClick(course.id, schedule.id, it.originalDay)
                                    }
                                },
                                onCellLongClick = { day, period -> openAddDialogFor(day, period) },
                                onCourseLongClick = { course, schedule ->
                                    state.allSchedules.firstOrNull { it.id == schedule.id }?.let { original ->
                                        editCourse = course; editSchedule = original; showEditDialog = true
                                    }
                                }
                            )
                            if (stopped.isNotEmpty()) {
                                Text("本周停课", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                val namesBySchedule = remember(state.allSchedules, state.allCourseMap) {
                                    state.allSchedules.associate { it.id to state.allCourseMap[it.courseId]?.name }
                                }
                                for (entry in stopped) {
                                    val name = namesBySchedule[entry.scheduleId] ?: "课程"
                                    TextButton(onClick = { onOpenStoppedClass(entry) }) {
                                        Text("${LocalDate.ofEpochDay(entry.originalDay)} · $name · 调整或撤销")
                                    }
                                }
                            }
                            if (active.isEmpty() && stopped.isEmpty()) {
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
            AnimatedContent(
                targetState = weekRange,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 })
                        .togetherWith(fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 2 })
                },
                label = "weekRange"
            ) { range ->
                if (range.isNotEmpty()) {
                    Text(range, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
