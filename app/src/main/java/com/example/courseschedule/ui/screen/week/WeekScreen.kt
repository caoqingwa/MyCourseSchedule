package com.example.courseschedule.ui.screen.week

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.ui.component.AddCourseDialog
import com.example.courseschedule.ui.component.CourseScheduleTopBar
import com.example.courseschedule.ui.component.EditCourseDialog
import com.example.courseschedule.ui.component.SemesterSetupDialog
import com.example.courseschedule.ui.component.WeekGrid
import com.example.courseschedule.ui.navigation.NavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    val switcherState = rememberWeekSwitcherState()

    fun switchToWeek(target: Int) {
        val clamped = target.coerceIn(1, state.totalWeeks)
        if (clamped == state.selectedWeek) return
        scope.launch {
            switcherState.animateSwitch(
                fromWeek = state.selectedWeek,
                toWeek = clamped,
                screenWidthPx = screenWidthPx,
                onSwap = { viewModel.selectWeek(clamped) }
            )
        }
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(switcherState.offset.value.roundToInt(), 0) }
                                .graphicsLayer {
                                    scaleX = switcherState.scale.value
                                    scaleY = switcherState.scale.value
                                }
                                .alpha(switcherState.alpha.value)
                                .pointerInput(state.totalWeeks) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { switcherState.onDragStart() },
                                        onDragEnd = {
                                            val consumed = switcherState.onDragEnd(
                                                screenWidthPx = screenWidthPx,
                                                currentWeek = state.selectedWeek,
                                                totalWeeks = state.totalWeeks,
                                                scope = scope
                                            )
                                            if (consumed) {
                                                val target = switcherState.lastTarget
                                                if (target != null) {
                                                    switchToWeek(target)
                                                    switcherState.clearTarget()
                                                }
                                            }
                                        },
                                        onDragCancel = { switcherState.onDragCancel(scope) },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            switcherState.onDrag(
                                                delta = dragAmount,
                                                screenWidthPx = screenWidthPx,
                                                atBoundary = (dragAmount > 0 && state.selectedWeek <= 1) ||
                                                        (dragAmount < 0 && state.selectedWeek >= state.totalWeeks),
                                                scope = scope
                                            )
                                        }
                                    )
                                }
                        ) {
                            WeekGrid(
                                schedules = state.currentPage.schedules,
                                courses = state.currentPage.courseMap,
                                roomMap = state.currentPage.roomMap,
                                semester = state.semester,
                                currentDayOfWeek = state.currentDayOfWeek,
                                highlightDayOfWeek = if (state.highlightDayOfWeek > 0) state.highlightDayOfWeek
                                    else if (state.selectedWeek == state.currentWeek) state.currentDayOfWeek else 0,
                                selectedWeek = state.selectedWeek,
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
            courseRoom = "",
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
                viewModel.deleteCourse(course.id)
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
            onLoadPreset = { },
            onDeletePreset = { viewModel.deletePreset(it) }
        )
    }
}

// ── WeekSwitcherState ────────────────────────────────────────────────

@Stable
private class WeekSwitcherState {
    val offset = Animatable(0f)
    val alpha = Animatable(1f)
    val scale = Animatable(1f)

    private var animating = false
    private var pendingSwipes = 0
    private var lastDragDelta = 0f
    private var lastDragTime = 0L
    var lastTarget: Int? = null
        private set

    private val pageSpring = spring<Float>(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow
    )

    suspend fun animateSwitch(
        fromWeek: Int,
        toWeek: Int,
        screenWidthPx: Float,
        onSwap: () -> Unit
    ) {
        if (animating) {
            pendingSwipes += if (toWeek > fromWeek) 1 else -1
            return
        }
        animating = true
        // Phase 1: current page fades out + shrinks
        coroutineScope {
            launch { alpha.animateTo(0f, pageSpring) }
            launch { scale.animateTo(0.85f, pageSpring) }
        }
        // Phase 2: swap data
        onSwap()
        alpha.snapTo(0f)
        scale.snapTo(1.15f)
        // Phase 3: new page fades in + scales to normal
        coroutineScope {
            launch { alpha.animateTo(1f, pageSpring) }
            launch { scale.animateTo(1f, pageSpring) }
        }
        animating = false
        // Process pending swipes
        if (pendingSwipes != 0) {
            val remaining = pendingSwipes
            pendingSwipes = 0
            val next = (toWeek + remaining).coerceIn(1, 20)
            if (next != toWeek) lastTarget = next
        }
    }

    fun onDragStart() {
        if (!animating) pendingSwipes = 0
    }

    fun onDrag(delta: Float, screenWidthPx: Float, atBoundary: Boolean, scope: CoroutineScope) {
        if (animating) return
        val damped = if (atBoundary) delta * 0.25f else delta
        val newTarget = (offset.targetValue + damped).coerceIn(
            -screenWidthPx * 0.5f, screenWidthPx * 0.5f
        )
        scope.launch { offset.snapTo(newTarget) }
        lastDragDelta = delta
        lastDragTime = System.nanoTime()
    }

    fun onDragEnd(screenWidthPx: Float, currentWeek: Int, totalWeeks: Int, scope: CoroutineScope): Boolean {
        val dThresh = screenWidthPx * 0.15f
        val vThresh = 400f
        val elapsed = ((System.nanoTime() - lastDragTime) / 1_000_000f).coerceAtLeast(1f)
        val velocity = lastDragDelta / (elapsed / 1000f)
        val goRight = offset.value < -dThresh || velocity < -vThresh
        val goLeft = offset.value > dThresh || velocity > vThresh
        if (goRight || goLeft) {
            val delta = if (goRight) 1 else -1
            if (animating) {
                pendingSwipes += delta
                scope.launch { offset.animateTo(0f, pageSpring) }
                return false
            }
            val target = (currentWeek + delta).coerceIn(1, totalWeeks)
            if (target != currentWeek) {
                lastTarget = target
                scope.launch { offset.animateTo(0f, pageSpring) }
                return true
            }
        }
        // Not enough drag — snap back
        if (!animating) {
            scope.launch { offset.animateTo(0f, pageSpring) }
        }
        return false
    }

    fun onDragCancel(scope: CoroutineScope) {
        if (!animating) {
            scope.launch { offset.animateTo(0f, pageSpring) }
        }
    }

    fun clearTarget() {
        lastTarget = null
    }
}

@Composable
private fun rememberWeekSwitcherState(): WeekSwitcherState {
    return remember { WeekSwitcherState() }
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
