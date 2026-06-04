package com.example.courseschedule.ui.screen.week

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.ui.component.AddCourseDialog
import com.example.courseschedule.ui.component.EditCourseDialog
import com.example.courseschedule.ui.component.SemesterSetupDialog
import com.example.courseschedule.ui.component.WeekGrid
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
fun WeekScreen(
    onCourseClick: (Long) -> Unit,
    viewModel: WeekViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.consumeTargetWeek()
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSemesterDialog by remember { mutableStateOf(false) }
    var longPressDay by remember { mutableIntStateOf(1) }
    var longPressPeriod by remember { mutableIntStateOf(1) }
    var editCourse by remember { mutableStateOf<Course?>(null) }
    var editSchedule by remember { mutableStateOf<Schedule?>(null) }

    val selectedWeek = state.selectedWeek
    val swipeThreshold = 80f
    val scope = rememberCoroutineScope()

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val screenPx = with(density) { screenWidthDp.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(1f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\u8bfe\u7a0b\u8868", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(
                                "\u7b2c${selectedWeek}\u5468",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                fontSize = 12.sp,
                                color = if (selectedWeek != state.currentWeek) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (selectedWeek != state.currentWeek) {
                            Spacer(modifier = Modifier.width(6.dp))
                            FilledTonalButton(
                                onClick = { viewModel.selectWeek(state.currentWeek) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("\u672c\u5468", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSemesterDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "\u5b66\u671f\u8bbe\u7f6e")
                    }
                }
            )
        },
        content = { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { if (selectedWeek > 1) viewModel.selectWeek(selectedWeek - 1) }) {
                        Text("\u25c0 \u4e0a\u4e00\u5468", fontSize = 13.sp)
                    }
                    Text(
                        "\u7b2c${selectedWeek}\u5468 \u00b7 ${state.currentPage.weekRange}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { if (selectedWeek < state.totalWeeks) viewModel.selectWeek(selectedWeek + 1) }) {
                        Text("\u4e0b\u4e00\u5468 \u25b6", fontSize = 13.sp)
                    }
                }

                val weekScrollState = rememberScrollState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clipToBounds()
                        .pointerInput(selectedWeek) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val cur = offsetX.value
                                    val goNext = cur < -swipeThreshold && selectedWeek < state.totalWeeks
                                    val goPrev = cur > swipeThreshold && selectedWeek > 1
                                    scope.launch {
                                        if (goNext || goPrev) {
                                            val target = if (goNext) -screenPx else screenPx
                                            // Phase 1: slide + fade out simultaneously
                                            launch { contentAlpha.animateTo(0f, tween(180)) }
                                            offsetX.animateTo(target, tween(250, easing = FastOutSlowInEasing))
                                            // Phase 2: switch data while content is invisible
                                            viewModel.selectWeek(if (goNext) selectedWeek + 1 else selectedWeek - 1)
                                            // Phase 3: snap to opposite side
                                            offsetX.snapTo(-target)
                                            // Phase 4: slide + fade in simultaneously
                                            launch { contentAlpha.animateTo(1f, tween(180)) }
                                            offsetX.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
                                        } else {
                                            offsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 800f))
                                        }
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    scope.launch {
                                        val canGoLeft = selectedWeek < state.totalWeeks
                                        val canGoRight = selectedWeek > 1
                                        val newOffset = when {
                                            dragAmount > 0 && !canGoRight -> (offsetX.value + dragAmount * 0.3f).coerceAtMost(screenPx * 0.3f)
                                            dragAmount < 0 && !canGoLeft -> (offsetX.value + dragAmount * 0.3f).coerceAtLeast(-screenPx * 0.3f)
                                            else -> (offsetX.value + dragAmount).coerceIn(-screenPx, screenPx)
                                        }
                                        offsetX.snapTo(newOffset)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.TopStart
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(weekScrollState)
                            .graphicsLayer {
                                translationX = offsetX.value
                                alpha = contentAlpha.value
                            }
                    ) {
                        WeekGrid(
                            schedules = state.currentPage.schedules,
                            courses = state.currentPage.courseMap,
                            roomMap = state.currentPage.roomMap,
                            semester = state.semester,
                            currentDayOfWeek = state.currentDayOfWeek,
                            modifier = Modifier.fillMaxWidth(),
                            onCellClick = { course, _ -> onCourseClick(course.id) },
                            onCellLongClick = { day, period ->
                                longPressDay = day; longPressPeriod = period; showAddDialog = true
                            },
                            onCourseLongClick = { course, schedule ->
                                editCourse = course; editSchedule = schedule; showEditDialog = true
                            }
                        )
                    }
                }
            }
        }
    )

    if (showAddDialog) {
        AddCourseDialog(
            dayOfWeek = longPressDay, period = longPressPeriod,
            currentWeek = selectedWeek, totalWeeks = state.totalWeeks,
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
            onDismiss = { showSemesterDialog = false },
            onConfirm = { name, startDate, totalWeeks, periodCount, periodTimesJson ->
                viewModel.saveSemester(name, startDate, totalWeeks, periodCount, periodTimesJson)
                showSemesterDialog = false
            },
            onLoadPreset = { },
            onDeletePreset = { viewModel.deletePreset(it) }
        )
    }
}




