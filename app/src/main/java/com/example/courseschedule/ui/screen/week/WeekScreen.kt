package com.example.courseschedule.ui.screen.week

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.courseschedule.ui.component.WeekGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    onCourseClick: (Long) -> Unit,
    viewModel: WeekViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("课程表", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Text("第" + state.currentWeek + "周", modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        })
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("第" + state.currentWeek + "周 · " + state.weekRange, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            WeekGrid(
                schedules = state.schedules,
                courses = state.courseMap,
                currentDayOfWeek = state.currentDayOfWeek,
                onCellClick = { course, _ -> onCourseClick(course.id) }
            )
        }
    }
}
