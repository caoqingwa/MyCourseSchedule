package com.example.courseschedule.ui.screen.week

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.courseschedule.ui.component.AddCourseDialog
import com.example.courseschedule.ui.component.WeekGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    onCourseClick: (Long) -> Unit,
    viewModel: WeekViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var longPressDay by remember { mutableIntStateOf(1) }
    var longPressPeriod by remember { mutableIntStateOf(1) }
    var selectedWeek by remember { mutableIntStateOf(state.currentWeek) }

    LaunchedEffect(state.currentWeek) { selectedWeek = state.currentWeek }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\u8bfe\u7a0b\u8868", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        "\u7b2c" + selectedWeek + "\u5468",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        })

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { if (selectedWeek > 1) selectedWeek-- }) {
                Text("\u25c0 \u4e0a\u4e00\u5468", fontSize = 13.sp)
            }
            Text(
                "\u7b2c" + selectedWeek + "\u5468 \u00b7 " + state.weekRange,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { if (selectedWeek < state.totalWeeks) selectedWeek++ }) {
                Text("\u4e0b\u4e00\u5468 \u25b6", fontSize = 13.sp)
            }
        }

        if (selectedWeek == state.currentWeek) {
            Text(
                "\u2190 \u53ef\u5de6\u53f3\u5207\u6362\u5468\u6570",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            WeekGrid(
                schedules = state.schedules,
                courses = state.courseMap,
                currentDayOfWeek = state.currentDayOfWeek,
                onCellClick = { course, _ -> onCourseClick(course.id) },
                onCellLongClick = { day, period ->
                    longPressDay = day
                    longPressPeriod = period
                    showAddDialog = true
                }
            )
        }
    }

    if (showAddDialog) {
        AddCourseDialog(
            dayOfWeek = longPressDay,
            period = longPressPeriod,
            currentWeek = selectedWeek,
            totalWeeks = state.totalWeeks,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, teacher, room, weekType, startWeek, endWeek, startPeriod, endPeriod ->
                viewModel.addCourse(
                    dayOfWeek = longPressDay,
                    name = name,
                    teacher = teacher,
                    room = room,
                    weekType = weekType,
                    startWeek = startWeek,
                    endWeek = endWeek,
                    startPeriod = startPeriod,
                    endPeriod = endPeriod
                )
                showAddDialog = false
            }
        )
    }
}
