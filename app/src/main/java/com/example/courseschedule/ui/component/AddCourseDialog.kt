package com.example.courseschedule.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
    onDismiss: () -> Unit,
    onConfirm: (name: String, teacher: String, room: String, weekType: Int, startWeek: Int, endWeek: Int, startPeriod: Int, endPeriod: Int) -> Unit
) {
    val dayNames = listOf("\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94")
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var weekTypeIndex by remember { mutableIntStateOf(0) }
    var startWeek by remember { mutableStateOf(currentWeek.toString()) }
    var endWeek by remember { mutableStateOf(totalWeeks.toString()) }
    var startPeriod by remember { mutableStateOf(period.toString()) }
    var endPeriod by remember { mutableStateOf(period.toString()) }
    var weekTypeExpanded by remember { mutableStateOf(false) }
    val weekTypes = listOf("\u5168\u5468", "\u5355\u5468(\u5947\u6570\u5468)", "\u53cc\u5468(\u5076\u6570\u5468)")

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
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("\u8bfe\u7a0b\u540d\u79f0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("\u6559\u5e08") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("\u6559\u5ba4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("\u5468\u7c7b\u578b", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(
                    expanded = weekTypeExpanded,
                    onExpandedChange = { weekTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = weekTypes[weekTypeIndex],
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weekTypeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = weekTypeExpanded,
                        onDismissRequest = { weekTypeExpanded = false }
                    ) {
                        weekTypes.forEachIndexed { idx, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { weekTypeIndex = idx; weekTypeExpanded = false }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startWeek,
                        onValueChange = { startWeek = it.filter { c -> c.isDigit() } },
                        label = { Text("\u8d77\u5468") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endWeek,
                        onValueChange = { endWeek = it.filter { c -> c.isDigit() } },
                        label = { Text("\u6b63\u5468") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startPeriod,
                        onValueChange = { startPeriod = it.filter { c -> c.isDigit() } },
                        label = { Text("\u8d77\u8282") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endPeriod,
                        onValueChange = { endPeriod = it.filter { c -> c.isDigit() } },
                        label = { Text("\u6b63\u8282") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name.trim(),
                            teacher.trim(),
                            room.trim(),
                            weekTypeIndex,
                            startWeek.toIntOrNull() ?: currentWeek,
                            endWeek.toIntOrNull() ?: totalWeeks,
                            startPeriod.toIntOrNull() ?: period,
                            endPeriod.toIntOrNull() ?: period
                        )
                    }
                }
            ) { Text("\u786e\u5b9a") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") }
        }
    )
}