package com.example.courseschedule.ui.screen.week

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.math.absoluteValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

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
    val scope = rememberCoroutineScope()

    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    // Real-time follow-finger offset (set directly, no coroutine)
    var visualOffset by remember { mutableFloatStateOf(0f) }
    // Animatable only used for snap-back animation after release
    val snapBackOffset = remember { Animatable(0f) }
    var isAnimatingBack by remember { mutableStateOf(false) }


    val bounceBackSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    // Compute the actual translation: either live drag or animating back
    val currentTranslationX by remember {
        derivedStateOf {
            if (isAnimatingBack) snapBackOffset.value else visualOffset
        }
    }
    fun performWeekSwitch(newWeek: Int) {
        visualOffset = 0f; isAnimatingBack = false
        viewModel.selectWeek(newWeek)
    }

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
                                onClick = { performWeekSwitch(state.currentWeek) },
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
                // Week navigation bar with arrows
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (selectedWeek > 1) performWeekSwitch(selectedWeek - 1) },
                            enabled = selectedWeek > 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                        val weekRange = state.currentPage.weekRange
                        if (weekRange.isNotEmpty()) {
                            Text(weekRange, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = { if (selectedWeek < state.totalWeeks) performWeekSwitch(selectedWeek + 1) },
                            enabled = selectedWeek < state.totalWeeks,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                    }
                    if (selectedWeek != state.currentWeek) {
                        FilledTonalButton(
                            onClick = { performWeekSwitch(state.currentWeek) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("\u672c\u5468", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }



                // Grid area with horizontal swipe
                val weekScrollState = rememberScrollState()
                var hDragAccum by remember { mutableFloatStateOf(0f) }
                var hLastDx by remember { mutableFloatStateOf(0f) }
                var hDragging by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(weekScrollState)
                    ) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer { translationX = currentTranslationX }
                                .pointerInput(selectedWeek, state.totalWeeks) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { hDragAccum = 0f; hDragging = true },
                                        onDragEnd = {
                                            val velocity = hLastDx / 0.016f
                                            val vThresh = 800f
                                            val dThresh = screenWidthPx * 0.3f
                                            val cur = viewModel.uiState.value.selectedWeek
                                            val goRight = velocity < -vThresh || hDragAccum < -dThresh
                                            val goLeft = velocity > vThresh || hDragAccum > dThresh
                                            if ((goRight && cur < state.totalWeeks) || (goLeft && cur > 1)) {
                                                if (goRight) performWeekSwitch(cur + 1)
                                                else performWeekSwitch(cur - 1)
                                            } else {
                                                isAnimatingBack = true
                                                scope.launch {
                                                    snapBackOffset.snapTo(visualOffset)
                                                    snapBackOffset.animateTo(0f, bounceBackSpring)
                                                    isAnimatingBack = false
                                                    visualOffset = 0f
                                                }
                                            }
                                            hDragAccum = 0f; hDragging = false
                                        },
                                        onDragCancel = { hDragAccum = 0f; hDragging = false },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            hDragAccum += dragAmount
                                            hLastDx = dragAmount
                                            val cur = viewModel.uiState.value.selectedWeek
                                            val atLeft = hDragAccum > 0 && cur <= 1
                                            val atRight = hDragAccum < 0 && cur >= state.totalWeeks
                                            val deadZone = screenWidthPx * 0.05f
                                            val effective = if (hDragAccum > 0) (hDragAccum - deadZone).coerceAtLeast(0f) else (hDragAccum + deadZone).coerceAtMost(0f)
                                            visualOffset = if (atLeft || atRight) effective * 0.25f
                                            else effective.coerceIn(-screenWidthPx * 0.5f, screenWidthPx * 0.5f)
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



