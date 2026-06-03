package com.example.courseschedule.ui.screen.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.courseschedule.ui.component.CourseCard
import com.example.courseschedule.util.DateUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onCourseClick: (Long) -> Unit,
    viewModel: TodayViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val todayStr = SimpleDateFormat("yyyy年M月d日 · EEEE", Locale.CHINESE).format(Date())

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("课程表", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(8.dp))
                state.semester?.let { sem ->
                    val wk = DateUtils.getWeekNumber(System.currentTimeMillis(), sem.startDate)
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                        Text("第" + wk + "周", modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        })
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(todayStr, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                Text("今天", modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        if (state.isEmpty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😊", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("今天没有课程，好好休息吧", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                item { Text("今日剩余 " + state.totalRemaining + " 节课", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                state.currentCourse?.let { cws ->
                    item {
                        val nxt = state.upcomingCourses.firstOrNull()?.let { "下一节：" + it.course.name + " " + it.schedule.startPeriod + "-" + it.schedule.endPeriod + "节" }
                        CourseCard(cws, isCurrent = true, nextInfo = nxt, onClick = { onCourseClick(cws.course.id) })
                    }
                }
                itemsIndexed(state.upcomingCourses) { idx, item ->
                    val nxt = if (idx < state.upcomingCourses.lastIndex) state.upcomingCourses[idx+1].let { "下一节：" + it.course.name + " " + it.schedule.startPeriod + "-" + it.schedule.endPeriod + "节" } else null
                    CourseCard(item, isCurrent = false, nextInfo = nxt, onClick = { onCourseClick(item.course.id) })
                }
            }
        }
    }
}
