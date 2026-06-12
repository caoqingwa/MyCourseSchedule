package com.example.courseschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.ui.theme.CourseColors
import com.example.courseschedule.util.DateUtils

@Immutable
data class CourseWithSchedule(val course: Course, val schedule: Schedule, val roomName: String? = null)

@Composable
fun CourseCard(item: CourseWithSchedule, isCurrent: Boolean, nextInfo: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colorIdx = item.course.id.toInt().mod(CourseColors.size)
    val (bg, fg) = CourseColors[colorIdx]
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isCurrent) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                else Modifier
            )
            .background(bg)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Text(item.course.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = fg, modifier = Modifier.weight(1f))
            if (isCurrent) {
                Surface(color = Color(0xFF2E7D32), shape = RoundedCornerShape(10.dp)) {
                    Text(" \u2714 \u5f53\u524d ", fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Text(DateUtils.getPeriodTimeRangeStatic(item.schedule.startPeriod, item.schedule.endPeriod), fontSize = 12.sp, color = fg.copy(alpha = 0.7f))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\ud83d\udccd ", fontSize = 13.sp, modifier = Modifier.width(18.dp))
            Text(item.roomName ?: "\u5f85\u5206\u914d", fontSize = 12.sp, color = fg.copy(alpha = 0.85f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\ud83d\udc64 ", fontSize = 13.sp, modifier = Modifier.width(18.dp))
            Text(item.course.teacher.ifBlank { "\u5f85\u586b\u5199" }, fontSize = 12.sp, color = fg.copy(alpha = 0.85f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\u23f0 ", fontSize = 13.sp, modifier = Modifier.width(18.dp))
            Text("\u7b2c" + item.schedule.startPeriod + "-" + item.schedule.endPeriod + "\u8282", fontSize = 12.sp, color = fg.copy(alpha = 0.85f))
        }
        if (nextInfo != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("\u23ed " + nextInfo, fontSize = 11.sp, color = fg.copy(alpha = 0.7f))
        }
    }
}

