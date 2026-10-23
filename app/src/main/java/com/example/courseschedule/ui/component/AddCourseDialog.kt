package com.example.courseschedule.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.util.DateUtils
import com.example.courseschedule.util.toggleWeekPattern
import com.example.courseschedule.util.weeksForSpan
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseDialog(
    dayOfWeek: Int,
    period: Int,
    currentWeek: Int,
    totalWeeks: Int,
    periodCount: Int = 12,
    weekDays: Int = 5,
    conflicts: List<Pair<String, Int>> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, teacher: String, room: String, weeks: Set<Int>, startPeriod: Int, endPeriod: Int) -> Unit,
) {
    val dayNames = if (weekDays == 7) DateUtils.DAY_NAMES_7 else DateUtils.DAY_NAMES_5
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var teacher by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var selectedWeeks by remember {
        mutableStateOf((currentWeek.coerceIn(1, totalWeeks)..totalWeeks).toSet())
    }
    var weeksError by remember { mutableStateOf(false) }
    var startPeriod by remember { mutableStateOf(period.toString()) }
    var endPeriod by remember { mutableStateOf(minOf(period + 1, periodCount).toString()) }
    var showConflictWarning by remember { mutableStateOf(false) }

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
                    if (!periodError && selectedWeeks.isNotEmpty()) {
                        onConfirm(name.trim(), teacher.trim(), room.trim(), selectedWeeks,
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
                WeekSelectionSection(
                    totalWeeks = totalWeeks,
                    selectedWeeks = selectedWeeks,
                    weeksError = weeksError,
                    onSelectionChange = { selectedWeeks = it; weeksError = false }
                )
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
                if (name.isBlank()) {
                    nameError = true
                } else if (selectedWeeks.isEmpty()) {
                    weeksError = true
                } else if (!periodError) {
                    if (conflicts.isEmpty()) {
                        onConfirm(name.trim(), teacher.trim(), room.trim(), selectedWeeks,
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

@Composable
private fun WeekSquareSelector(
    totalWeeks: Int,
    selectedWeeks: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
) {
    if (totalWeeks <= 0) return
    val columns = minOf(totalWeeks, 5)
    val gap = 4.dp
    val minCellSize = 32.dp
    val maxCellSize = 42.dp
    val rows = (totalWeeks + columns - 1) / columns
    val shape = RoundedCornerShape(6.dp)
    val latestSelected by rememberUpdatedState(selectedWeeks)
    val latestOnChange by rememberUpdatedState(onSelectionChange)

    BoxWithConstraints {
        val availableWidth = if (maxWidth.value.isFinite()) {
            maxWidth
        } else {
            maxCellSize * columns + gap * (columns - 1)
        }
        val cellSize = ((availableWidth - gap * (columns - 1)) / columns)
            .coerceIn(minCellSize, maxCellSize)
        val width = cellSize * columns + gap * (columns - 1)
        val density = LocalDensity.current
        val cellPx = with(density) { cellSize.toPx() }
        val stepPx = with(density) { (cellSize + gap).toPx() }

        fun indexAt(position: Offset): Int? {
            if (position.x < 0f || position.y < 0f) return null
            val col = (position.x / stepPx).toInt()
            val row = (position.y / stepPx).toInt()
            if (col !in 0 until columns || row !in 0 until rows) return null
            if (position.x - col * stepPx > cellPx || position.y - row * stepPx > cellPx) return null
            return (row * columns + col).takeIf { it < totalWeeks }?.plus(1)
        }

        Column(
            modifier = Modifier
                .width(width)
                .pointerInput(totalWeeks, columns, cellPx, stepPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startWeek = indexAt(down.position) ?: return@awaitEachGesture
                        val selecting = startWeek !in latestSelected
                        val working = latestSelected.toMutableSet()
                        var lastWeek = startWeek
                        var dragging = false

                        fun paint(week: Int) {
                            if (selecting) working.add(week) else working.remove(week)
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!dragging) {
                                if (!change.pressed) {
                                    if (startWeek in working) working.remove(startWeek) else working.add(startWeek)
                                    latestOnChange(working.toSet())
                                    break
                                }
                                val dx = abs(change.position.x - down.position.x)
                                val dy = abs(change.position.y - down.position.y)
                                if (dx < viewConfiguration.touchSlop && dy < viewConfiguration.touchSlop) continue
                                dragging = true
                                paint(startWeek)
                                latestOnChange(working.toSet())
                            }
                            if (dragging) {
                                change.consume()
                                val week = indexAt(change.position)
                                if (week != null && week != lastWeek) {
                                    val range = if (week > lastWeek) (lastWeek + 1)..week else week..(lastWeek - 1)
                                    range.forEach(::paint)
                                    lastWeek = week
                                    latestOnChange(working.toSet())
                                }
                                if (!change.pressed) break
                            }
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0 until columns) {
                        val week = row * columns + col + 1
                        if (week > totalWeeks) continue
                        val selected = week in selectedWeeks
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .clip(shape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape
                                )
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "\u7b2c${week}\u5468"
                                    stateDescription = if (selected) "\u5df2\u9009" else "\u672a\u9009"
                                    role = Role.Checkbox
                                    onClick(
                                        label = if (selected) "\u53d6\u6d88\u9009\u62e9\u7b2c${week}\u5468"
                                        else "\u9009\u62e9\u7b2c${week}\u5468"
                                    ) {
                                        val next = selectedWeeks.toMutableSet()
                                        if (selected) next.remove(week) else next.add(week)
                                        onSelectionChange(next)
                                        true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$week",
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekSelectionSection(
    totalWeeks: Int,
    selectedWeeks: Set<Int>,
    weeksError: Boolean,
    onSelectionChange: (Set<Int>) -> Unit,
) {
    Text("\u4e0a\u8bfe\u5468\u6b21", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        WeekSquareSelector(
            totalWeeks = totalWeeks,
            selectedWeeks = selectedWeeks,
            onSelectionChange = onSelectionChange
        )
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        WeekQuickActions(
            totalWeeks = totalWeeks,
            selectedWeeks = selectedWeeks,
            onSelectionChange = onSelectionChange
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "\u5df2\u9009 ${selectedWeeks.size} \u5468",
            fontSize = 12.sp,
            color = if (weeksError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "\u70b9\u6309\u6216\u62d6\u52a8\u9009\u62e9",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (weeksError) {
        Text(
            "\u81f3\u5c11\u9009\u62e9\u4e00\u5468",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekQuickActions(
    totalWeeks: Int,
    selectedWeeks: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
) {
    val allWeeks = remember(totalWeeks) { (1..totalWeeks).toSet() }
    val oddWeeks = remember(totalWeeks) { allWeeks.filter { it % 2 == 1 }.toSet() }
    val evenWeeks = remember(totalWeeks) { allWeeks.filter { it % 2 == 0 }.toSet() }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WeekPatternChip("\u5168\u5468", allWeeks, selectedWeeks) {
            onSelectionChange(toggleWeekPattern(selectedWeeks, allWeeks))
        }
        WeekPatternChip("\u5355\u5468", oddWeeks, selectedWeeks) {
            onSelectionChange(toggleWeekPattern(selectedWeeks, oddWeeks))
        }
        WeekPatternChip("\u53cc\u5468", evenWeeks, selectedWeeks) {
            onSelectionChange(toggleWeekPattern(selectedWeeks, evenWeeks))
        }
    }
}

@Composable
private fun WeekPatternChip(
    label: String,
    pattern: Set<Int>,
    selectedWeeks: Set<Int>,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = pattern.isNotEmpty() && selectedWeeks.containsAll(pattern),
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) }
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
    weekDays: Int = 5,
    initialWeeks: Set<Int>? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, teacher: String, room: String, dayOfWeek: Int, weeks: Set<Int>, startPeriod: Int, endPeriod: Int) -> Unit,
    onDelete: () -> Unit
) {
    val dayNames = if (weekDays == 7) DateUtils.DAY_NAMES_7 else DateUtils.DAY_NAMES_5
    var name by remember { mutableStateOf(courseName) }
    var nameError by remember { mutableStateOf(false) }
    var teacher by remember { mutableStateOf(courseTeacher) }
    var room by remember { mutableStateOf(courseRoom) }
    val defaultWeeks = remember(initialWeeks, startWeek, endWeek, weekType, totalWeeks) {
        (initialWeeks ?: weeksForSpan(startWeek, endWeek, weekType))
            .filter { it in 1..totalWeeks }.toSet()
    }
    var selectedWeeks by remember { mutableStateOf(defaultWeeks) }
    var weeksError by remember { mutableStateOf(false) }
    var sPeriod by remember { mutableStateOf(startPeriod.toString()) }
    var ePeriod by remember { mutableStateOf(endPeriod.toString()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val spVal = sPeriod.toIntOrNull()
    val epVal = ePeriod.toIntOrNull()
    val periodError = (spVal != null && (spVal < 1 || spVal > periodCount)) ||
            (epVal != null && (epVal < 1 || epVal > periodCount)) ||
            (spVal != null && epVal != null && spVal > epVal)

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("\u786e\u8ba4\u5220\u9664") },
            text = { Text("\u786e\u5b9a\u8981\u5220\u9664\u201c${courseName}\u201d\u5728\u8be5\u65f6\u95f4\u6bb5\u7684\u5168\u90e8\u5468\u6b21\u5417\uff1f\u6b64\u64cd\u4f5c\u4e0d\u53ef\u64a4\u9500\u3002") },
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
                WeekSelectionSection(
                    totalWeeks = totalWeeks,
                    selectedWeeks = selectedWeeks,
                    weeksError = weeksError,
                    onSelectionChange = { selectedWeeks = it; weeksError = false }
                )
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
                if (name.isBlank()) {
                    nameError = true
                } else if (selectedWeeks.isEmpty()) {
                    weeksError = true
                } else if (!periodError) {
                    onConfirm(name.trim(), teacher.trim(), room.trim(), dayOfWeek, selectedWeeks,
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
