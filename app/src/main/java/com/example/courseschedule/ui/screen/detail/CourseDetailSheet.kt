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
    roomName: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(course.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 16.dp))

            DetailRow(icon = "\ud83d\udccd", label = "\u6559\u5ba4", value = roomName ?: "\u5f85\u5206\u914d")
            DetailRow(icon = "\ud83d\udc64", label = "\u6559\u5e08", value = course.teacher)

            val weekTypes = mapOf(0 to "\u5168\u5468", 1 to "\u5355\u5468", 2 to "\u53cc\u5468")
            val weekInfo = schedules.firstOrNull()?.let {
                "\u7b2c" + it.startWeek + "-" + it.endWeek + "\u5468" + (weekTypes[it.weekType] ?: "")
            } ?: "\u5f85\u914d\u7f6e"
            DetailRow(icon = "\ud83d\udcc5", label = "\u8bfe\u7a0b\u5468\u6570", value = weekInfo)

            val dayNames = listOf("\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94")
            val scheduleInfo = schedules.joinToString(", ") {
                dayNames.getOrElse(it.dayOfWeek - 1) { "?" } + " " + it.startPeriod + "-" + it.endPeriod + "\u8282"
            }
            DetailRow(icon = "\u23f0", label = "\u65f6\u95f4\u5b89\u6392", value = scheduleInfo.ifEmpty { "\u5f85\u914d\u7f6e" })

            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("\u5173\u95ed") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onEdit) { Text("\u7f16\u8f91") }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(icon: String, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 14.sp, modifier = Modifier.width(32.dp))
        Column {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
