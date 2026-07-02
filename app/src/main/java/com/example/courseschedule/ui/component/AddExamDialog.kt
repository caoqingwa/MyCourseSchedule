package com.example.courseschedule.ui.component

import android.app.DatePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Exam
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExamDialog(
    courses: List<Course>,
    initialDateMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (courseId: Long, examDate: Long, reminderHours: Int, notes: String?) -> Unit,
    editingExam: Exam? = null,
    onDelete: (() -> Unit)? = null
) {
    val isEditing = editingExam != null

    val initialCourseIndex = if (isEditing && courses.isNotEmpty()) {
        courses.indexOfFirst { it.id == editingExam!!.courseId }.coerceIn(0, courses.lastIndex)
    } else 0

    var selectedCourseIndex by remember { mutableIntStateOf(initialCourseIndex) }
    // Keep index in bounds if courses list shrinks
    if (courses.isNotEmpty() && selectedCourseIndex >= courses.size) {
        selectedCourseIndex = courses.lastIndex
    }

    val initCal = Calendar.getInstance().apply {
        timeInMillis = editingExam?.examDate ?: initialDateMillis
    }
    var year by remember { mutableIntStateOf(initCal.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(initCal.get(Calendar.MONTH)) }
    var day by remember { mutableIntStateOf(initCal.get(Calendar.DAY_OF_MONTH)) }
    var hour by remember { mutableIntStateOf(initCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf((initCal.get(Calendar.MINUTE) / 15) * 15) }

    fun buildDateMillis(): Long {
        return Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val defaultReminderHours = editingExam?.reminderHours ?: 48
    var reminderHours by remember { mutableStateOf(defaultReminderHours.toString()) }
    var notes by remember { mutableStateOf(editingExam?.notes ?: "") }
    var courseExpanded by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy\u5e74M\u6708d\u65e5", Locale.getDefault()) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEditing) "\u7f16\u8f91\u8003\u8bd5" else "\u6dfb\u52a0\u8003\u8bd5",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (isEditing && onDelete != null) {
                    IconButton(onClick = {
                        onDelete()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "\u5220\u9664", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (courses.isEmpty()) {
                    Text(
                        "\u8bf7\u5148\u6dfb\u52a0\u8bfe\u7a0b",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("\u5173\u8054\u8bfe\u7a0b", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ExposedDropdownMenuBox(
                        expanded = courseExpanded,
                        onExpandedChange = { courseExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = courses.getOrNull(selectedCourseIndex)?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = courseExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = courseExpanded,
                            onDismissRequest = { courseExpanded = false }
                        ) {
                            courses.forEachIndexed { idx, course ->
                                DropdownMenuItem(
                                    text = { Text(course.name) },
                                    onClick = { selectedCourseIndex = idx; courseExpanded = false }
                                )
                            }
                        }
                    }

                    Text("\u8003\u8bd5\u65e5\u671f", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = dateFormat.format(Date(buildDateMillis())),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    year = y
                                    month = m
                                    day = d
                                },
                                year, month, day
                            ).show()
                        }
                    )

                    Text("\u8003\u8bd5\u65f6\u95f4", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ScrollTimePicker(
                        hour = hour,
                        minute = minute,
                        onHourChange = { hour = it },
                        onMinuteChange = { minute = it }
                    )

                    Text("\u63d0\u524d\u63d0\u9192", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = reminderHours,
                        onValueChange = { reminderHours = it.filter { c -> c.isDigit() } },
                        label = { Text("\u5c0f\u65f6\u6570") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("\u5907\u6ce8\uff08\u53ef\u9009\uff09", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("\u5982\uff1a\u671f\u4e2d\u8003\u8bd5\u3001\u95ed\u5377\u7b49") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val course = courses.getOrNull(selectedCourseIndex) ?: return@TextButton
                    onConfirm(
                        course.id,
                        buildDateMillis(),
                        reminderHours.toIntOrNull() ?: 48,
                        notes.ifBlank { null }
                    )
                },
                enabled = courses.isNotEmpty() && selectedCourseIndex in courses.indices
            ) { Text(if (isEditing) "\u4fdd\u5b58" else "\u786e\u5b9a") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") }
        }
    )
}

private const val ITEM_HEIGHT = 36

@Composable
private fun ScrollTimePicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    val hours = (0..23).toList()
    val minutes = (0..59 step 15).toList()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SnapWheel(
            count = 24,
            selected = hour,
            label = { String.format("%02d", it) },
            onSelected = onHourChange,
            modifier = Modifier.weight(1f)
        )
        Text(":", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 2.dp))
        SnapWheel(
            count = 4,
            selected = minute / 15,
            label = { String.format("%02d", it * 15) },
            onSelected = { onMinuteChange(it * 15) },
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SnapWheel(
    count: Int,
    selected: Int,
    label: (Int) -> String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selected
    )

    // Detect centered item via scroll offset
    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) return@derivedStateOf selected

            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val center = viewportHeight / 2 + layoutInfo.viewportStartOffset

            var bestIndex = layoutInfo.visibleItemsInfo.first().index
            var bestDist = Int.MAX_VALUE
            for (item in layoutInfo.visibleItemsInfo) {
                val itemCenter = item.offset + item.size / 2
                val dist = abs(itemCenter - center)
                if (dist < bestDist) {
                    bestDist = dist
                    bestIndex = item.index
                }
            }
            bestIndex.coerceIn(0, count - 1)
        }
    }

    // Report selection when scroll settles
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            onSelected(centeredIndex)
        }
    }

    // Snap to nearest on settle via fling
    LaunchedEffect(Unit) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val target = centeredIndex
                    val itemOffset = listState.layoutInfo.visibleItemsInfo
                        .find { it.index == target }?.offset ?: return@collect
                    val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                    val targetOffset = itemOffset - (viewportHeight / 2) + (listState.layoutInfo.visibleItemsInfo.find { it.index == target }?.size ?: ITEM_HEIGHT) / 2
                    if (abs(itemOffset - (viewportHeight / 2)) > 2) {
                        listState.animateScrollToItem(target, scrollOffset = -(ITEM_HEIGHT / 2))
                    }
                }
            }
    }

    // Scroll to selected when externally changed
    LaunchedEffect(selected) {
        listState.animateScrollToItem(selected, scrollOffset = -(ITEM_HEIGHT / 2))
    }

    val paddingDp = with(androidx.compose.ui.platform.LocalDensity.current) { (ITEM_HEIGHT * 1).toDp() }

    Box(modifier = modifier.height(ITEM_HEIGHT.dp * 3)) {
        // Center highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = ITEM_HEIGHT.dp)
        ) {
            items(count) { index ->
                val isSelected = index == centeredIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ITEM_HEIGHT.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label(index),
                        fontSize = if (isSelected) 18.sp else 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }
}