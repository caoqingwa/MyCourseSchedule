package com.example.courseschedule.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.courseschedule.data.importer.CourseImportResult
import com.example.courseschedule.data.importer.ImportedCourse
import com.example.courseschedule.data.importer.ImportUpdate
import com.example.courseschedule.data.importer.ImportWorksheet
import com.example.courseschedule.data.importer.ParsedCourseImport
import com.example.courseschedule.data.importer.XlsxCourseParser
import com.example.courseschedule.data.importer.XlsxCourseWriter
import com.example.courseschedule.ui.screen.week.WeekViewModel.ImportPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CourseImportFlow(
    requestKey: Int,
    onRequestHandled: () -> Unit,
    previewImport: suspend (ParsedCourseImport) -> ImportPreview,
    importCourses: suspend (Long, List<ImportedCourse>, List<ImportUpdate>) -> CourseImportResult,
) {
    val scope = rememberCoroutineScope()
    val resolver = LocalContext.current.contentResolver
    var progress by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<CourseImportResult?>(null) }
    var worksheets by remember { mutableStateOf(emptyList<ImportWorksheet>()) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }

    fun perform(label: String, action: suspend () -> Unit) {
        if (progress != null) return
        progress = label
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "无法导入课表"
            } finally {
                progress = null
            }
        }
    }

    suspend fun prepare(sheet: ImportWorksheet) {
        require(sheet.data.courses.isNotEmpty() || sheet.data.issues.isNotEmpty()) {
            "「${sheet.name}」没有课程。请填写模板后重新导入。"
        }
        preview = withContext(Dispatchers.Default) { previewImport(sheet.data) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform("正在读取课表") {
            val sheets = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use(XlsxCourseParser::parse)
                    ?: error("无法读取课表文件")
            }
            if (sheets.size == 1) prepare(sheets.single()) else worksheets = sheets
        }
    }

    LaunchedEffect(requestKey) {
        if (requestKey > 0) {
            onRequestHandled()
            importLauncher.launch(arrayOf(XlsxCourseWriter.MIME_TYPE, "application/octet-stream", "*/*"))
        }
    }

    progress?.let { label ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(label) },
            text = { CircularProgressIndicator() },
            confirmButton = {}
        )
    }

    if (worksheets.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { worksheets = emptyList() },
            title = { Text("选择课表工作表") },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(worksheets) { sheet ->
                        TextButton(onClick = {
                            worksheets = emptyList()
                            perform("正在准备导入预览") { prepare(sheet) }
                        }) {
                            Text("${sheet.name} · ${sheet.data.courses.size} 条课程记录")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { worksheets = emptyList() }) { Text("取消") } }
        )
    }

    preview?.let { plan ->
        var applyUpdates by remember(plan) { mutableStateOf(false) }
        var showUpdates by remember(plan) { mutableStateOf(plan.courses.isEmpty()) }
        var showIssues by remember(plan) { mutableStateOf(plan.courses.isEmpty()) }
        var showConflicts by remember(plan) { mutableStateOf(false) }
        val conflictCount = plan.conflictedSlots.size

        fun save(skipConflicts: Boolean) {
            val updates = if (applyUpdates) plan.updates else emptyList()
            preview = null
            perform("正在保存课表") {
                val courses = if (skipConflicts) withContext(Dispatchers.Default) {
                    plan.courses.mapNotNull { course ->
                        val schedules = course.schedules.filterNot { (course.sourceRow to it) in plan.conflictedSlots }
                        if (schedules.isEmpty()) null else course.copy(schedules = schedules)
                    }
                } else plan.courses
                result = importCourses(plan.semesterId, courses, updates)
            }
        }

        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("导入课程确认") },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    item {
                        Text("${plan.semesterName} · 可新增 ${plan.scheduleCount} 个时段")
                        if (plan.duplicates > 0) Text("重复时段将跳过（${plan.duplicates}）")
                    }
                    items(plan.courses.take(8)) { Text("• ${it.name}", maxLines = 1) }
                    if (plan.courses.size > 8) item { Text("共 ${plan.courses.size} 条课程记录") }
                    if (plan.updates.isNotEmpty()) {
                        item {
                            TextButton(onClick = { showUpdates = !showUpdates }) {
                                Text(if (showUpdates) "收起已有排课变更" else "查看已有排课变更（${plan.updates.size}）")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().toggleable(
                                    value = applyUpdates, role = Role.Checkbox, onValueChange = { applyUpdates = it }
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = applyUpdates, onCheckedChange = null)
                                Text("应用 ${plan.updates.size} 项教师或教室变更")
                            }
                        }
                        if (showUpdates) items(plan.updates) { Text(it.description) }
                    }
                    if (plan.issues.isNotEmpty()) {
                        item {
                            TextButton(onClick = { showIssues = !showIssues }) {
                                Text(if (showIssues) "收起跳过项" else "查看跳过项（${plan.issues.size}）")
                            }
                        }
                        if (showIssues) items(plan.issues) { Text("第 ${it.row} 行 · ${it.reason}") }
                    }
                    if (conflictCount > 0) {
                        item {
                            Text("$conflictCount 个排课时段存在冲突", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { showConflicts = !showConflicts }) {
                                Text(if (showConflicts) "收起冲突明细" else "查看冲突明细")
                            }
                        }
                        if (showConflicts) items(plan.conflicts) {
                            val slot = it.schedule
                            val day = "一二三四五六日"[slot.dayOfWeek - 1]
                            Text(
                                "第 ${it.sourceRow} 行 · ${it.importedCourseName} 与 ${it.existingName}" +
                                    "（周$day 第 ${slot.startPeriod}–${slot.endPeriod} 节）",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = plan.scheduleCount > 0 || applyUpdates, onClick = { save(false) }) {
                    Text(if (conflictCount > 0) "全部导入" else if (plan.scheduleCount > 0) "导入" else "更新")
                }
            },
            dismissButton = {
                Row {
                    if (conflictCount > 0) {
                        TextButton(
                            enabled = plan.scheduleCount > conflictCount || applyUpdates,
                            onClick = { save(true) }
                        ) { Text("跳过冲突导入") }
                    }
                    TextButton(onClick = { preview = null }) { Text("取消") }
                }
            }
        )
    }

    result?.let {
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text("导入完成") },
            text = { Text("导入 ${it.courseCount} 门课程的 ${it.scheduleCount} 个时段，更新 ${it.updateCount} 项已有信息。") },
            confirmButton = { TextButton(onClick = { result = null }) { Text("确定") } }
        )
    }
    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("导入失败") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("确定") } }
        )
    }
}
