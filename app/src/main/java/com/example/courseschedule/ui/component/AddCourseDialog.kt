package com.example.courseschedule.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseDialog(
    dayOfWeek: Int,
    period: Int,
    currentWeek: Int,
    totalWeeks: Int,
    periodCount: Int = 12,
    conflicts: List<Pair<String, Int>> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, teacher: String, room: String, weekType: Int, startWeek: Int, endWeek: Int, startPeriod: Int, endPeriod: Int) -> Unit,
) {
    val dayNames = listOf("\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94")
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var teacher by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var weekTypeIndex by remember { mutableIntStateOf(0) }
    var startWeek by remember { mutableStateOf(currentWeek.toString()) }
    var endWeek by remember { mutableStateOf(totalWeeks.toString()) }
    var startPeriod by remember { mutableStateOf(period.toString()) }
    var endPeriod by remember { mutableStateOf(period.toString()) }
    var weekTypeExpanded by remember { mutableStateOf(false) }
    var showConflictWarning by remember { mutableStateOf(false) }
    val weekTypes = listOf("\u5168\u5468", "\u5355\u5468(\u5947\u6570\u5468)", "\u53cc\u5468(\u5076\u6570\u5468)")

    val spVal = startPeriod.toIntOrNull()
    val epVal = endPeriod.toIntOrNull()
    val periodError = (spVal != null && (spVal < 1 || spVal > periodCount)) ||
            (epVal != null && (epVal < 1 || epVal > periodCount)) ||
            (spVal != null && epVal != null && spVal > epVal)

    if (showConflictWarning) {
        AlertDialog(
            onDismissRequest = { showConflictWarning = false },
            title = { Text("\u65f6\u95f4\u51b2\u7a81\u63d0\u793a") },
            text = {
                Column {
                    Text("\u4ee5\u4e0b\u8bfe\u7a0b\u4e0e\u65b0\u589e\u8bfe\u7a0b\u65f6\u95f4\u91cd\u53e0:")
                    Spacer(modifier = Modifier.height(8.dp))
                    conflicts.forEach { (courseName, period) ->
                        Text(
                            "  \u2022 $courseName (\u7b2c${period}\u8282)",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("\u662f\u5426\u7ee7\u7eed\u6dfb\u52a0?", fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConflictWarning = false
                    if (!periodError) {
                        onConfirm(name.trim(), teacher.trim(), room.trim(), weekTypeIndex,
                            startWeek.toIntOrNull() ?: currentWeek, endWeek.toIntOrNull() ?: totalWeeks,
                            startPeriod.toIntOrNull() ?: period, endPeriod.toIntOrNull() ?: period)
                    }
                }) { Text("\u7ee7\u7eed\u6dfb\u52a0") }
            },
            dismissButton = {
                TextButton(onClick = { showConflictWarning = false }) { Text("\u53d6\u6d88") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("\u6dfb\u52a0\u8bfe\u7a0b - ${dayNames.getOrElse(dayOfWeek - 1) { "?" }} \u7b2c${period}\u8282")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; nameError = false },
                    label = { Text("\u8bfe\u7a0b\u540d\u79f0") },
                    singleLine = true, isError = nameError, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacher, onValueChange = { teacher = it },
                    label = { Text("\u6559\u5e08") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = room, onValueChange = { room = it },
                    label = { Text("\u6559\u5ba4") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("\u5468\u7c7b\u578b", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(expanded = weekTypeExpanded, onExpandedChange = { weekTypeExpanded = it }) {
                    OutlinedTextField(
                        value = weekTypes[weekTypeIndex], onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weekTypeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = weekTypeExpanded, onDismissRequest = { weekTypeExpanded = false }) {
                        weekTypes.forEachIndexed { idx, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { weekTypeIndex = idx; weekTypeExpanded = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startWeek, onValueChange = { startWeek = it.filter { c -> c.isDigit() } },
                        label = { Text("\u8d77\u5468") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endWeek, onValueChange = { endWeek = it.filter { c -> c.isDigit() } },
                        label = { Text("\u6b63\u5468") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startPeriod, onValueChange = { startPeriod = it.filter { c -> c.isDigit() } },
                        label = { Text("\u8d77\u8282") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f),
                        isError = spVal != null && (spVal < 1 || spVal > periodCount),
                        supportingText = if (spVal != null && (spVal < 1 || spVal > periodCount)) {
                            { Text("\u9650\u5236: 1~$periodCount\u8282", fontSize = 11.sp) }
                        } else null
                    )
                    OutlinedTextField(
                        value = endPeriod, onValueChange = { endPeriod = it.filter { c -> c.isDigit() } },
                        label = { Text("\u6b63\u8282") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f),
                        isError = epVal != null && (epVal < 1 || epVal > periodCount),
                        supportingText = if (epVal != null && (epVal < 1 || epVal > periodCount)) {
                            { Text("\u9650\u5236: 1~$periodCount\u8282", fontSize = 11.sp) }
                        } else null
                    )
                }
                if (spVal != null && epVal != null && spVal > epVal) {
                    Text(
                        "\u8d77\u8282\u4e0d\u80fd\u5927\u4e8e\u6b63\u8282",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                    if (name.isBlank()) { nameError = true } else if (periodError) { /* no-op, errors shown inline */ } else {
                    if (conflicts.isEmpty()) {
                        onConfirm(name.trim(), teacher.trim(), room.trim(), weekTypeIndex,
                            startWeek.toIntOrNull() ?: currentWeek, endWeek.toIntOrNull() ?: totalWeeks,
                            startPeriod.toIntOrNull() ?: period, endPeriod.toIntOrNull() ?: period)
                    } else {
                        showConflictWarning = true
                    }
                }
            }) { Text("\u786e\u5b9a") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCourseDialog(
    courseName: String,
    courseTeacher: String,
    courseRoom: String,
    dayOfWeek: Int,
    startPeriod: Int,
    endPeriod: Int,
    startWeek: Int,
    endWeek: Int,
    weekType: Int,
    totalWeeks: Int,
    periodCount: Int = 12,
    onDismiss: () -> Unit,
    onConfirm: (name: String, teacher: String, room: String, dayOfWeek: Int, weekType: Int, startWeek: Int, endWeek: Int, startPeriod: Int, endPeriod: Int) -> Unit,
    onDelete: () -> Unit
) {
    val dayNames = listOf("\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94")
    var name by remember { mutableStateOf(courseName) }
    var nameError by remember { mutableStateOf(false) }
    var teacher by remember { mutableStateOf(courseTeacher) }
    var room by remember { mutableStateOf(courseRoom) }
    var weekTypeIndex by remember { mutableIntStateOf(weekType) }
    var sWeek by remember { mutableStateOf(startWeek.toString()) }
    var eWeek by remember { mutableStateOf(endWeek.toString()) }
    var sPeriod by remember { mutableStateOf(startPeriod.toString()) }
    var ePeriod by remember { mutableStateOf(endPeriod.toString()) }
    var weekTypeExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val weekTypes = listOf("\u5168\u5468", "\u5355\u5468(\u5947\u6570\u5468)", "\u53cc\u5468(\u5076\u6570\u5468)")

    val spVal = sPeriod.toIntOrNull()
    val epVal = ePeriod.toIntOrNull()
    val periodError = (spVal != null && (spVal < 1 || spVal > periodCount)) ||
            (epVal != null && (epVal < 1 || epVal > periodCount)) ||
            (spVal != null && epVal != null && spVal > epVal)

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("\u786e\u8ba4\u5220\u9664") },
            text = { Text("\u786e\u5b9a\u8981\u5220\u9664\u8bfe\u7a0b\u300c${courseName}\u300d\u5417\uff1f\u6b64\u64cd\u4f5c\u4e0d\u53ef\u64a4\u9500\u3002") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("\u5220\u9664", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("\u53d6\u6d88") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("\u7f16\u8f91\u8bfe\u7a0b - ${dayNames.getOrElse(dayOfWeek - 1) { "?" }}")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; nameError = false },
                    label = { Text("\u8bfe\u7a0b\u540d\u79f0") },
                    singleLine = true, isError = nameError, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacher, onValueChange = { teacher = it },
                    label = { Text("\u6559\u5e08") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = room, onValueChange = { room = it },
                    label = { Text("\u6559\u5ba4") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("\u5468\u7c7b\u578b", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(expanded = weekTypeExpanded, onExpandedChange = { weekTypeExpanded = it }) {
                    OutlinedTextField(
                        value = weekTypes[weekTypeIndex], onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weekTypeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = weekTypeExpanded, onDismissRequest = { weekTypeExpanded = false }) {
                        weekTypes.forEachIndexed { idx, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { weekTypeIndex = idx; weekTypeExpanded = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = sWeek, onValueChange = { sWeek = it.filter { c -> c.isDigit() } },
                        label = { Text("\u8d77\u5468") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = eWeek, onValueChange = { eWeek = it.filter { c -> c.isDigit() } },
                        label = { Text("\u6b63\u5468") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = sPeriod, onValueChange = { sPeriod = it.filter { c -> c.isDigit() } },
                        label = { Text("\u8d77\u8282") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f),
                        isError = spVal != null && (spVal < 1 || spVal > periodCount),
                        supportingText = if (spVal != null && (spVal < 1 || spVal > periodCount)) {
                            { Text("\u9650\u5236: 1~$periodCount\u8282", fontSize = 11.sp) }
                        } else null
                    )
                    OutlinedTextField(
                        value = ePeriod, onValueChange = { ePeriod = it.filter { c -> c.isDigit() } },
                        label = { Text("\u6b63\u8282") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f),
                        isError = epVal != null && (epVal < 1 || epVal > periodCount),
                        supportingText = if (epVal != null && (epVal < 1 || epVal > periodCount)) {
                            { Text("\u9650\u5236: 1~$periodCount\u8282", fontSize = 11.sp) }
                        } else null
                    )
                }
                if (spVal != null && epVal != null && spVal > epVal) {
                    Text(
                        "\u8d77\u8282\u4e0d\u80fd\u5927\u4e8e\u6b63\u8282",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { nameError = true } else if (!periodError) {
                    onConfirm(name.trim(), teacher.trim(), room.trim(), dayOfWeek, weekTypeIndex,
                        sWeek.toIntOrNull() ?: startWeek, eWeek.toIntOrNull() ?: endWeek,
                        sPeriod.toIntOrNull() ?: startPeriod, ePeriod.toIntOrNull() ?: endPeriod)
                }
            }) { Text("\u4fdd\u5b58") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("\u5220\u9664", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") }
            }
        }
    )
}
