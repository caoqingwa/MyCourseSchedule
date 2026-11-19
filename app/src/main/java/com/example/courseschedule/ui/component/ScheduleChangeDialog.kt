package com.example.courseschedule.ui.component

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.courseschedule.data.db.entity.ScheduleChange
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ScheduleChangeDialog(
    initial: ScheduleChange,
    existing: Boolean,
    semesterDays: ClosedRange<LocalDate>?,
    onSave: suspend (ScheduleChange, Boolean) -> List<String>,
    onUndo: suspend () -> Unit,
    onDismiss: () -> Unit
) {
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(initial.targetDay ?: initial.originalDay)) }
    var start by remember { mutableStateOf(initial.startPeriod.toString()) }
    var end by remember { mutableStateOf(initial.endPeriod.toString()) }
    var room by remember { mutableStateOf(initial.roomName.orEmpty()) }
    var keepOriginal by remember { mutableStateOf(initial.keepOriginal) }
    var stopped by remember { mutableStateOf(initial.targetDay == null) }
    var saved by remember { mutableStateOf(existing) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var conflict by remember { mutableStateOf<Pair<ScheduleChange, List<String>>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun submit(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "操作失败"
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("调整本次 · ${LocalDate.ofEpochDay(initial.originalDay)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    FilterChip(stopped, { stopped = !stopped }, { Text("停课") }, enabled = !busy)
                    Spacer(Modifier.width(8.dp))
                    FilterChip(keepOriginal && !stopped, { keepOriginal = !keepOriginal }, { Text("加一堂补课") }, enabled = !stopped && !busy)
                }
                if (!stopped) {
                    OutlinedButton(enabled = !busy, onClick = {
                        val zone = ZoneId.systemDefault()
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> date = LocalDate.of(year, month + 1, day) },
                            date.year, date.monthValue - 1, date.dayOfMonth
                        ).apply {
                            semesterDays?.let {
                                datePicker.minDate = it.start.atStartOfDay(zone).toInstant().toEpochMilli()
                                datePicker.maxDate = it.endInclusive.atStartOfDay(zone).toInstant().toEpochMilli()
                            }
                        }.show()
                    }) { Text("上课日期 · $date") }
                    OutlinedTextField(start, { start = it }, label = { Text("开始节次") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(end, { end = it }, label = { Text("结束节次") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(room, { room = it }, label = { Text("教室") }, singleLine = true)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                submit {
                    val updated = initial.copy(
                        targetDay = if (stopped) null else date.toEpochDay(),
                        startPeriod = if (stopped) initial.startPeriod else start.toIntOrNull() ?: error("请输入开始节次"),
                        endPeriod = if (stopped) initial.endPeriod else end.toIntOrNull() ?: error("请输入结束节次"),
                        roomName = room.trim().ifEmpty { null },
                        keepOriginal = !stopped && keepOriginal
                    )
                    val conflicts = onSave(updated, false)
                    if (conflicts.isEmpty()) {
                        if (stopped) saved = true else onDismiss()
                    } else conflict = updated to conflicts
                }
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (saved) TextButton(enabled = !busy, onClick = { submit { onUndo(); onDismiss() } }) { Text("撤销调整") }
                TextButton(enabled = !busy, onClick = onDismiss) { Text("关闭") }
            }
        }
    )
    conflict?.let { (change, names) ->
        AlertDialog(
            onDismissRequest = { conflict = null },
            title = { Text("目标节次有冲突") },
            text = { Text(names.joinToString("、")) },
            confirmButton = {
                TextButton(onClick = {
                    conflict = null
                    submit { onSave(change, true); onDismiss() }
                }) { Text("仍然保存") }
            },
            dismissButton = { TextButton(onClick = { conflict = null }) { Text("返回修改") } }
        )
    }
}
