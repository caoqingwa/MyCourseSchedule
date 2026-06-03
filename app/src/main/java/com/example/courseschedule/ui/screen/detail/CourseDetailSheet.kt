package com.example.courseschedule.ui.screen.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    course: Course,
    schedules: List<Schedule>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(course.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 16.dp))

            DetailRow(icon = "📍", label = "教室", value = "待实现")
            DetailRow(icon = "👨‍🏫", label = "教师", value = course.teacher)

            val weekTypes = mapOf(0 to "全周", 1 to "单周", 2 to "双周")
            val weekInfo = schedules.firstOrNull()?.let { "第" + it.startWeek + "-" + it.endWeek + "周 " + (weekTypes[it.weekType] ?: "") } ?: "待配置"
            DetailRow(icon = "📅", label = "课程周数", value = weekInfo)

            val scheduleInfo = schedules.joinToString(", ") { "周" + it.dayOfWeek + " " + it.startPeriod + "-" + it.endPeriod + "节" }
            DetailRow(icon = "🕐", label = "时间安排", value = scheduleInfo.ifEmpty { "待配置" })

            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("关闭") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onEdit) { Text("编辑") }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(icon: String, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 14.sp, modifier = Modifier.width(32.dp))
        Column { Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) }
    }
}
