package com.example.courseschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.util.DateUtils

data class CourseWithSchedule(val course: Course, val schedule: Schedule)

@Composable
fun CourseCard(item: CourseWithSchedule, isCurrent: Boolean, nextInfo: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = Color(android.graphics.Color.parseColor(item.course.color))
    val textColor = Color(android.graphics.Color.parseColor("#1A3A10"))
    Column(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Text(item.course.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.weight(1f))
            Text(DateUtils.getPeriodTimeRange(item.schedule.startPeriod, item.schedule.endPeriod), fontSize = 12.sp, color = textColor.copy(alpha = 0.7f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("\ud83d\udccd " + item.course.name, fontSize = 13.sp, color = textColor.copy(alpha = 0.85f))
        Text("\ud83d\udd50 \u7b2c" + item.schedule.startPeriod + "-" + item.schedule.endPeriod + "\u8282", fontSize = 13.sp, color = textColor.copy(alpha = 0.85f))
        if (nextInfo != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("\u23ed " + nextInfo, fontSize = 12.sp, color = textColor.copy(alpha = 0.7f))
        }
    }
}
