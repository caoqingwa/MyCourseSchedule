package com.example.courseschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.util.DateUtils
import java.util.*

@Composable
fun CalendarPicker(
    currentMonth: Int,
    currentYear: Int,
    todayMillis: Long,
    semesterStartMillis: Long,
    currentWeek: Int,
    onDayClick: (Long) -> Unit,
    onMonthChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dows = listOf("\u4e00", "\u4e8c", "\u4e09", "\u56db", "\u4e94", "\u516d", "\u65e5")
    val cal = Calendar.getInstance()
    cal.set(currentYear, currentMonth, 1)
    val firstDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    val daysInMonth = cal.get(Calendar.DAY_OF_MONTH)

    Column(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("\u25c0", modifier = Modifier.clickable { onMonthChange(if (currentMonth == 0) 11 else currentMonth - 1, if (currentMonth == 0) currentYear - 1 else currentYear) }.padding(8.dp), fontSize = 16.sp)
            Text(currentYear.toString() + "\u5e74" + (currentMonth + 1).toString() + "\u6708", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("\u25b6", modifier = Modifier.clickable { onMonthChange(if (currentMonth == 11) 0 else currentMonth + 1, if (currentMonth == 11) currentYear + 1 else currentYear) }.padding(8.dp), fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row { dows.forEach { Text(it, modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        var day = 1
        for (row in 0..5) {
            if (day > daysInMonth) break
            Row {
                for (col in 0..6) {
                    if (row == 0 && col < firstDayOfWeek || day > daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f).height(32.dp))
                    } else {
                        val d = day
                        val cal2 = Calendar.getInstance().apply { set(currentYear, currentMonth, d) }
                        val dayMillis = cal2.timeInMillis
                        val isToday = DateUtils.isToday(dayMillis)
                        val weekNum = DateUtils.getWeekNumber(dayMillis, semesterStartMillis)
                        val inWeek = weekNum == currentWeek
                        Box(modifier = Modifier.weight(1f).height(32.dp).clip(CircleShape)
                            .background(when { isToday -> MaterialTheme.colorScheme.primary; inWeek -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surfaceContainer })
                            .clickable { onDayClick(dayMillis) },
                            contentAlignment = Alignment.Center) {
                            Text(d.toString(), fontSize = 12.sp, color = when { isToday -> MaterialTheme.colorScheme.onPrimary; inWeek -> MaterialTheme.colorScheme.onPrimaryContainer; else -> MaterialTheme.colorScheme.onSurface })
                        }
                        day++
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("\u5f53\u524d\uff1a\u7b2c" + currentWeek + "\u5468 \u00b7 " + java.text.SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(semesterStartMillis + (currentWeek - 1).toLong() * 7 * 86400000L)) + " ~ " + java.text.SimpleDateFormat("MM.dd", Locale.getDefault()).format(Date(semesterStartMillis + (currentWeek - 1).toLong() * 7 * 86400000L + 6 * 86400000L)), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
