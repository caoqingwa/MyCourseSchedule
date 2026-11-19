package com.example.courseschedule.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.data.importer.ImportedCourse
import com.example.courseschedule.data.importer.ImportRowIssue
import com.example.courseschedule.data.importer.ParsedCourseImport
import com.example.courseschedule.data.importer.XlsxCourseParser
import com.example.courseschedule.ui.screen.week.WeekViewModel.ImportConflict
import com.example.courseschedule.ui.screen.week.WeekViewModel.ImportPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * xlsx 课表导入的完整流程（选择文件 → 确认 → 结果/错误）。
 *
 * 之前这套逻辑藏在周课表页顶栏齿轮打开的学期设置弹窗里，入口极深；
 * 现在提到 MainActivity，全局设置里的学期设置也能直接导入。
 *
 * @param requestKey 每次自增表示「请打开文件选择器」，处理完回调 [onRequestHandled] 复位
 */
@Composable
fun CourseImportFlow(
    requestKey: Int,
    onRequestHandled: () -> Unit,
    previewImport: suspend (ParsedCourseImport) -> ImportPreview,
    importCourses: suspend (Long, List<ImportedCourse>) -> Pair<Int, Int>,
) {
    var importResult by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var parsing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var rowIssues by remember { mutableStateOf<List<ImportRowIssue>>(emptyList()) }
    var duplicateCount by remember { mutableIntStateOf(0) }
    var semesterName by remember { mutableStateOf("") }
    var semesterId by remember { mutableStateOf(0L) }
    var showRowIssues by remember { mutableStateOf(false) }
    var parsedCourses by remember { mutableStateOf<List<ImportedCourse>>(emptyList()) }
    var importConflicts by remember { mutableStateOf<List<ImportConflict>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun save(courses: List<ImportedCourse>) {
        if (saving || courses.isEmpty()) return
        val targetSemester = semesterId
        saving = true
        showImportConfirm = false
        scope.launch {
            try {
                importResult = importCourses(targetSemester, courses)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                importError = e.message ?: "无法保存课表"
            } finally {
                saving = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            parsing = true
            scope.launch {
                try {
                    val parsed = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use(XlsxCourseParser::parse)
                            ?: error("无法读取课表文件")
                    }
                    val courses = parsed.courses
                    if (courses.isEmpty() && parsed.issues.isEmpty()) {
                        importError =
                            "\u672a\u89e3\u6790\u51fa\u6709\u6548\u8bfe\u7a0b\uff08\u8bf7\u786e\u8ba4\u8868\u683c\u5305\u542b\u201c\u8bfe\u7a0b\u540d\u79f0\u201d\u4e0e\u201c\u4e0a\u8bfe\u65f6\u95f4\u201d\u5217\uff09"
                    } else {
                        val preview = previewImport(parsed)
                        parsedCourses = preview.courses
                        rowIssues = preview.issues
                        showRowIssues = preview.courses.isEmpty()
                        importConflicts = preview.conflicts
                        duplicateCount = preview.duplicates
                        semesterName = preview.semesterName
                        semesterId = preview.semesterId
                        showImportConfirm = true
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    importError = "\u5bfc\u5165\u5931\u8d25\uff1a${e.message}"
                } finally {
                    parsing = false
                }
            }
        }
    }

    LaunchedEffect(requestKey) {
        if (requestKey > 0) {
            onRequestHandled()
            importLauncher.launch(
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/octet-stream",
                    "*/*"
                )
            )
        }
    }

    if (parsing || saving) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (saving) "正在保存课表" else "正在读取课表") },
            text = { CircularProgressIndicator() },
            confirmButton = {}
        )
    }

    // 导入结果确认
    if (showImportConfirm) {
        val conflictCount = importConflicts.size
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("\u5bfc\u5165\u8bfe\u7a0b\u786e\u8ba4") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "${semesterName} · 可新增 ${parsedCourses.sumOf { it.schedules.size }} 个时段",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (duplicateCount > 0) Text("重复时段将跳过（$duplicateCount）")
                    parsedCourses.take(8).forEach {
                        Text("\u2022 ${it.name}", fontSize = 13.sp, maxLines = 1)
                    }
                    if (parsedCourses.size > 8) {
                        Text(
                            "...\u7b49${parsedCourses.size}\u95e8",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (rowIssues.isNotEmpty()) {
                        TextButton(onClick = { showRowIssues = !showRowIssues }) {
                            val action = if (showRowIssues) "收起" else "查看"
                            Text("${action}跳过项（${rowIssues.size}）")
                        }
                        if (showRowIssues) {
                            rowIssues.forEach { issue ->
                                Text("第 ${issue.row} 行 · ${issue.reason}")
                            }
                        }
                    }
                    if (conflictCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "\u26a0 \u4e0e\u5df2\u6709\u8bfe\u7a0b\u65f6\u95f4\u51b2\u7a81\u7684\u65f6\u6bb5 ${conflictCount} \u4e2a\uff1a",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                        val dayNames = listOf(
                            "\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db",
                            "\u5468\u4e94", "\u5468\u516d", "\u5468\u65e5"
                        )
                        importConflicts.take(6).forEach {
                            Text(
                                "\u2022 ${it.importedCourseName} \u4e0e ${it.existingName} \uff08${dayNames.getOrElse(it.dayOfWeek - 1) { "?" }}\u7b2c${it.startPeriod}-${it.endPeriod}\u8282\uff09",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1
                            )
                        }
                        if (conflictCount > 6) {
                            Text(
                                "...\u7b49${conflictCount}\u4e2a\u51b2\u7a81",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = parsedCourses.isNotEmpty(), onClick = {
                    save(parsedCourses)
                }) { Text("\u5168\u90e8\u5bfc\u5165") }
            },
            dismissButton = {
                Row {
                    if (conflictCount > 0) {
                        val conflictNames = importConflicts.map { it.importedCourseName }.toSet()
                        val remaining = parsedCourses.filter { it.name !in conflictNames }
                        TextButton(enabled = remaining.isNotEmpty(), onClick = {
                            save(remaining)
                        }) {
                            Text(
                                "\u8df3\u8fc7\u51b2\u7a81\u5bfc\u5165",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    TextButton(onClick = {
                        showImportConfirm = false
                        parsedCourses = emptyList()
                        importConflicts = emptyList()
                    }) { Text("\u53d6\u6d88") }
                }
            }
        )
    }

    importResult?.let { (courses, schedules) ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("\u5bfc\u5165\u5b8c\u6210") },
            text = { Text("\u6210\u529f\u5bfc\u5165 ${courses} \u95e8\u8bfe\u7a0b\u3001${schedules} \u4e2a\u6392\u8bfe\u65f6\u6bb5\u3002") },
            confirmButton = {
                TextButton(onClick = { importResult = null }) { Text("\u597d") }
            }
        )
    }

    importError?.let { msg ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("\u5bfc\u5165\u5931\u8d25") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { importError = null }) { Text("\u597d") }
            }
        )
    }
}
