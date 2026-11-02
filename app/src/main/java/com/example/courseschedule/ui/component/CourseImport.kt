package com.example.courseschedule.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.courseschedule.data.importer.XlsxCourseParser
import com.example.courseschedule.ui.screen.week.WeekViewModel.ImportConflict
import kotlinx.coroutines.launch

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
    checkConflicts: suspend (List<ImportedCourse>) -> List<ImportConflict>,
    importCourses: suspend (List<ImportedCourse>) -> Pair<Int, Int>,
) {
    var importResult by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var parsedCourses by remember { mutableStateOf<List<ImportedCourse>>(emptyList()) }
    var importConflicts by remember { mutableStateOf<List<ImportConflict>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val courses = context.contentResolver.openInputStream(uri)?.use {
                        XlsxCourseParser.parse(it)
                    } ?: emptyList()
                    if (courses.isEmpty()) {
                        importError =
                            "\u672a\u89e3\u6790\u51fa\u6709\u6548\u8bfe\u7a0b\uff08\u8bf7\u786e\u8ba4\u8868\u683c\u5305\u542b\u201c\u8bfe\u7a0b\u540d\u79f0\u201d\u4e0e\u201c\u4e0a\u8bfe\u65f6\u95f4\u201d\u5217\uff09"
                    } else {
                        parsedCourses = courses
                        importConflicts = checkConflicts(courses)
                        showImportConfirm = true
                    }
                } catch (e: Exception) {
                    importError = "\u5bfc\u5165\u5931\u8d25\uff1a${e.message}"
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

    // 导入结果确认
    if (showImportConfirm && parsedCourses.isNotEmpty()) {
        val conflictCount = importConflicts.size
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("\u5bfc\u5165\u8bfe\u7a0b\u786e\u8ba4") },
            text = {
                Column {
                    Text(
                        "\u53d1\u73b0 ${parsedCourses.size} \u95e8\u8bfe\u7a0b\uff0c\u5c06\u5bfc\u5165\u5f53\u524d\u5b66\u671f\uff1a",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                TextButton(onClick = {
                    showImportConfirm = false
                    val toImport = parsedCourses
                    parsedCourses = emptyList()
                    importConflicts = emptyList()
                    scope.launch {
                        try {
                            val (courses, schedules) = importCourses(toImport)
                            importResult = courses to schedules
                        } catch (e: Exception) {
                            importError = "\u5bfc\u5165\u5931\u8d25\uff1a${e.message}"
                        }
                    }
                }) { Text("\u5168\u90e8\u5bfc\u5165") }
            },
            dismissButton = {
                Row {
                    if (conflictCount > 0) {
                        TextButton(onClick = {
                            showImportConfirm = false
                            val conflictNames = importConflicts.map { it.importedCourseName }.toSet()
                            val toImport = parsedCourses.filter { it.name !in conflictNames }
                            parsedCourses = emptyList()
                            importConflicts = emptyList()
                            scope.launch {
                                try {
                                    val (courses, schedules) = importCourses(toImport)
                                    importResult = courses to schedules
                                } catch (e: Exception) {
                                    importError = "\u5bfc\u5165\u5931\u8d25\uff1a${e.message}"
                                }
                            }
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
