package com.example.courseschedule.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.example.courseschedule.data.db.entity.Semester
import kotlin.math.roundToInt
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterSetupDialog(
    semester: Semester?,
    savedPresets: List<Semester>,
    maxScheduledPeriod: Int = 0,
    hasWeekendCourses: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, startDateMillis: Long, totalWeeks: Int, periodCount: Int, weekDays: Int, periodTimesJson: String) -> Unit,
    onLoadPreset: (Semester) -> Unit,
    onDeletePreset: (Semester) -> Unit
) {
    val cal = Calendar.getInstance().apply {
        timeInMillis = semester?.startDate ?: System.currentTimeMillis()
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    var name by remember { mutableStateOf(semester?.name ?: "") }
    var selectedYear by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(cal.get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableIntStateOf(cal.get(Calendar.DAY_OF_MONTH)) }
    var totalWeeks by remember { mutableIntStateOf(semester?.totalWeeks ?: 20) }
    var periodCount by remember { mutableIntStateOf(semester?.periodCount ?: 12) }
    var weekDays by remember { mutableIntStateOf(semester?.weekDays ?: 5) }
    var periodTimes by remember {
        mutableStateOf(semester?.getPeriodTimes() ?: Semester.defaultPeriodTimes())
    }

    var showPresetList by remember { mutableStateOf(false) }
    var editingPeriodIndex by remember { mutableIntStateOf(-1) }
    var editStartHour by remember { mutableIntStateOf(8) }
    var editStartMin by remember { mutableIntStateOf(0) }
    var editEndHour by remember { mutableIntStateOf(8) }
    var editEndMin by remember { mutableIntStateOf(45) }

    val outerScrollState = rememberScrollState()

    val maxDaysInMonth = remember(selectedYear, selectedMonth) {
        val m = Calendar.getInstance().apply { set(selectedYear, selectedMonth - 1, 1) }
        m.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    LaunchedEffect(maxDaysInMonth) {
        if (selectedDay > maxDaysInMonth) selectedDay = maxDaysInMonth
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(outerScrollState)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (semester == null) "\u8bbe\u7f6e\u5b66\u671f" else "\u7f16\u8f91\u5b66\u671f",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (savedPresets.isNotEmpty()) {
                    TextButton(onClick = { showPresetList = !showPresetList }) {
                        Text("\u9884\u8bbe", fontSize = 13.sp)
                    }
                }
            }

            // Presets
            if (showPresetList) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("\u5df2\u4fdd\u5b58\u9884\u8bbe", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        savedPresets.forEach { preset ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(preset.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { onLoadPreset(preset) }) { Text("\u52a0\u8f7d", fontSize = 12.sp) }
                                IconButton(onClick = { onDeletePreset(preset) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("\u5b66\u671f\u540d\u79f0") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Start date — Year/Month/Day pickers
            Text("\u5f00\u5b66\u65e5\u671f", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScrollNumberPicker(value = selectedYear, range = 2024..2030, label = "\u5e74",
                    onValueChange = { selectedYear = it }, modifier = Modifier.weight(1f))
                ScrollNumberPicker(value = selectedMonth, range = 1..12, label = "\u6708",
                    onValueChange = { selectedMonth = it }, modifier = Modifier.weight(1f))
                ScrollNumberPicker(value = selectedDay, range = 1..maxDaysInMonth, label = "\u65e5",
                    onValueChange = { selectedDay = it }, modifier = Modifier.weight(1f))
            }

            // Total weeks
            Text("\u603b\u5468\u6570", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { if (totalWeeks > 1) totalWeeks-- }) { Text("-", fontSize = 20.sp) }
                Spacer(modifier = Modifier.width(12.dp))
                Text("$totalWeeks", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Text("\u5468", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = { if (totalWeeks < 30) totalWeeks++ }) { Text("+", fontSize = 20.sp) }
            }

            // Week days (5 or 7)
            Text("\u6bcf\u5468\u8bfe\u8868\u5929\u6570", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val canSwitchToFive = !hasWeekendCourses
            if (hasWeekendCourses && weekDays == 7) {
                Text(
                    "\u26a0 \u5468\u672b\u5df2\u6709\u8bfe\u7a0b\uff0c\u65e0\u6cd5\u5207\u6362\u52305\u5929\u5236",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = weekDays == 5,
                    onClick = { if (canSwitchToFive) weekDays = 5 },
                    enabled = canSwitchToFive,
                    label = { Text("5\u5929 (\u5468\u4e00\u2013\u5468\u4e94)", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = weekDays == 7,
                    onClick = { weekDays = 7 },
                    label = { Text("7\u5929 (\u5468\u4e00\u2013\u5468\u65e5)", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Period count
            var showBlockedHint by remember { mutableStateOf(false) }
            Text("\u6bcf\u5929\u8282\u6570", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (showBlockedHint && maxScheduledPeriod > 0) {
                Text(
                    "\u26a0 \u5df2\u6709\u8bfe\u7a0b\u6392\u5230\u7b2c${maxScheduledPeriod}\u8282\uff0c\u65e0\u6cd5\u51cf\u5c11\u5230\u66f4\u4f4e",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Slider(
                value = periodCount.toFloat(),
                onValueChange = {
                    val v = it.roundToInt()
                    if (maxScheduledPeriod > 0 && v < maxScheduledPeriod) {
                        showBlockedHint = true
                        periodCount = maxScheduledPeriod
                    } else {
                        showBlockedHint = false
                        periodCount = v
                    }
                },
                valueRange = 4f..16f,
                steps = 11,
                onValueChangeFinished = {
                    val defaults = Semester.defaultPeriodTimes()
                    periodTimes = if (periodTimes.size > periodCount) {
                        periodTimes.take(periodCount)
                    } else if (periodTimes.size < periodCount) {
                        periodTimes + defaults.drop(periodTimes.size).take(periodCount - periodTimes.size)
                    } else periodTimes
                }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("4\u8282", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$periodCount\u8282", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("16\u8282", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Period time section — fixed total height container
            // The list + editor live inside this box so expanding the editor
            // doesn't push down the outer Column.
            Text("\u8bfe\u8282\u65f6\u95f4\u8bbe\u7f6e", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

            val isEditing = editingPeriodIndex in 0 until periodCount
            val sectionHeight = if (isEditing) 460.dp else 220.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sectionHeight)
            ) {
                // Period time list — always visible at top
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(periodCount) { i ->
                        val pt = periodTimes.getOrNull(i)
                        val startText = pt?.start ?: "??:??"
                        val endText = pt?.end ?: "??:??"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    editingPeriodIndex = if (editingPeriodIndex == i) -1 else i
                                    val s = pt?.start?.split(":")
                                    val e = pt?.end?.split(":")
                                    editStartHour = s?.getOrNull(0)?.toIntOrNull() ?: 8
                                    editStartMin = s?.getOrNull(1)?.toIntOrNull() ?: 0
                                    editEndHour = e?.getOrNull(0)?.toIntOrNull() ?: 8
                                    editEndMin = e?.getOrNull(1)?.toIntOrNull() ?: 45
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "\u7b2c${i + 1}\u8282", fontSize = 13.sp,
                                modifier = Modifier.width(48.dp), textAlign = TextAlign.Center, maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                                color = if (editingPeriodIndex == i)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "$startText ~ $endText",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Inline time editor — appears below the list, inside the same Box
                val editorHeight by animateDpAsState(
                    targetValue = if (isEditing) 240.dp else 0.dp,
                    animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
                    label = "editorHeight"
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = isEditing,
                    enter = fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)),
                    exit = fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)),
                    modifier = Modifier.align(Alignment.TopStart).offset(y = 220.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "\u7b2c${editingPeriodIndex.coerceAtLeast(0) + 1}\u8282 \u65f6\u95f4\u7f16\u8f91",
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("\u4e0a\u8bfe", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ScrollNumberPicker(value = editStartHour, range = 6..22, label = "\u65f6", onValueChange = { editStartHour = it }, modifier = Modifier.width(60.dp), fontSize = 13.sp)
                                        Text(":", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        ScrollNumberPicker(value = editStartMin, range = 0..55 step 5, label = "\u5206", onValueChange = { editStartMin = it }, modifier = Modifier.width(60.dp), fontSize = 13.sp)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("\u4e0b\u8bfe", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ScrollNumberPicker(value = editEndHour, range = 6..22, label = "\u65f6", onValueChange = { editEndHour = it }, modifier = Modifier.width(60.dp), fontSize = 13.sp)
                                        Text(":", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        ScrollNumberPicker(value = editEndMin, range = 0..55 step 5, label = "\u5206", onValueChange = { editEndMin = it }, modifier = Modifier.width(60.dp), fontSize = 13.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editingPeriodIndex = -1 }) { Text("\u53d6\u6d88") }
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(onClick = {
                                    val s = String.format("%02d:%02d", editStartHour, editStartMin)
                                    val e = String.format("%02d:%02d", editEndHour, editEndMin)
                                    periodTimes = periodTimes.toMutableList().also { list ->
                                        while (list.size <= editingPeriodIndex) list.add(Semester.PeriodTime("00:00", "00:00"))
                                        list[editingPeriodIndex] = Semester.PeriodTime(s, e)
                                    }
                                    editingPeriodIndex = -1
                                }) { Text("\u786e\u5b9a") }
                            }
                        }
                    }
                }
            }

            // Actions
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("\u53d6\u6d88") }
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val dateCal = Calendar.getInstance().apply {
                                set(selectedYear, selectedMonth - 1, selectedDay, 0, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            val json = Semester.buildPeriodTimesJson(periodTimes.take(periodCount))
                            onConfirm(name.trim(), dateCal.timeInMillis, totalWeeks, periodCount, weekDays, json)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("\u786e\u5b9a") }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ScrollNumberPicker(
    value: Int,
    range: IntProgression,
    label: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp
) {
    val items = range.toList()
    val initialIndex = items.indexOf(value).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val idx = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val itemHeight = 40
            val selected = if (offset > itemHeight / 2 && idx + 1 < items.size) idx + 1 else idx
            val v = items[selected.coerceIn(items.indices)]
            if (v != value) onValueChange(v)
        }
    }

    Box(
        modifier = modifier
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(items.size) { idx ->
                val v = items[idx]
                val isSelected = v == value
                Text(
                    text = "$v$label",
                    fontSize = if (isSelected) fontSize else fontSize * 0.85,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
        )
    }
}





