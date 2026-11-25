package com.example.courseschedule.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.courseschedule.data.db.ScheduleBackup
import com.example.courseschedule.data.importer.XlsxCourseWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BackupSettings(
    captureBackup: suspend () -> ScheduleBackup,
    restoreBackup: suspend (ScheduleBackup) -> Unit,
    hasRestoreSnapshot: suspend () -> Boolean,
    undoRestore: suspend () -> Unit,
    exportSemester: suspend (OutputStream) -> Unit,
    onImport: () -> Unit
) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ScheduleBackup?>(null) }
    var undoAvailable by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    var exportTemplate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { undoAvailable = hasRestoreSnapshot() }

    fun perform(label: String, action: suspend () -> Unit) {
        if (progress != null) return
        progress = label
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "备份操作失败"
            } finally {
                progress = null
                undoAvailable = hasRestoreSnapshot()
            }
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) perform("正在导出备份") {
            withContext(Dispatchers.IO) {
                val backup = captureBackup()
                require(backup.semesterNames.isNotEmpty()) { "没有可备份的学期" }
                val bytes = backup.encode()
                val output = resolver.openOutputStream(uri, "wt") ?: error("无法写入备份文件")
                output.use { it.write(bytes) }
            }
            message = "备份已导出"
        }
    }
    val exportExcel = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XlsxCourseWriter.MIME_TYPE)
    ) { uri ->
        val template = exportTemplate
        if (uri != null) perform(if (template) "正在保存模板" else "正在导出周课表") {
            withContext(Dispatchers.IO) {
                val output = resolver.openOutputStream(uri, "wt") ?: error("无法写入 Excel 文件")
                output.use {
                    if (template) XlsxCourseWriter.write(it) else exportSemester(it)
                }
            }
            message = if (template) {
                "Excel 模板已保存。每行填写一个时段，例如：星期一第1-2节{1-16周}。"
            } else "当前学期的周期课表已导出，可用 Excel 打开或重新导入。"
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform("正在读取备份") {
            preview = withContext(Dispatchers.IO) {
                val input = resolver.openInputStream(uri) ?: error("无法读取备份文件")
                input.use { ScheduleBackup.read(it) }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("导入、导出与备份", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onImport) { Text("导入 Excel 课表") }
            TextButton(onClick = {
                exportTemplate = true
                exportExcel.launch("CourseSchedule-template.xlsx")
            }) { Text("下载 Excel 模板") }
            TextButton(onClick = {
                exportTemplate = false
                exportExcel.launch("CourseSchedule-${LocalDate.now()}.xlsx")
            }) { Text("导出当前学期为 Excel") }
            Text("Excel 包含周期排课。换机时可导出包含考试和单次调课的 JSON 备份。",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { export.launch("CourseSchedule-backup-${LocalDate.now()}.json") }) {
                Text("导出备份")
            }
            TextButton(onClick = { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                Text("从文件恢复")
            }
            if (undoAvailable) {
                TextButton(onClick = { confirmUndo = true }) { Text("撤销上次恢复") }
            }
        }
    }

    progress?.let { label ->
        AlertDialog(onDismissRequest = {}, title = { Text(label) },
            text = { CircularProgressIndicator() }, confirmButton = {})
    }
    preview?.let { backup ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("恢复课表备份") },
            text = {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    Text(backup.semesterNames.joinToString("、"))
                    val timestamp = backup.createdAt?.let {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochMilli(it))
                    }
                    Text("备份时间 · ${timestamp ?: "旧版备份未记录"}")
                    Text("${backup.courseCount} 门课程 · ${backup.scheduleCount} 个排课时段")
                    Text("${backup.examCount} 场考试 · ${backup.changeCount} 条调课")
                    Spacer(Modifier.height(8.dp))
                    Text("恢复会替换全部学期及课表数据，并在本机保存一份恢复前的快照。")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preview = null
                    perform("正在恢复课表") {
                        restoreBackup(backup)
                        message = "课表已恢复"
                    }
                }) { Text("替换并恢复") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("取消") } }
        )
    }
    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text("撤销上次恢复？") },
            text = { Text("将回到上次恢复前的课表。恢复之后新增或修改的数据会被替换。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUndo = false
                    perform("正在撤销恢复") {
                        undoRestore()
                        message = "已回到上次恢复前的课表"
                    }
                }) { Text("确认撤销") }
            },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("取消") } }
        )
    }
    message?.let { result ->
        AlertDialog(onDismissRequest = { message = null }, text = { Text(result) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("确定") } },
            dismissButton = if (result == "课表已恢复" && undoAvailable) {
                { TextButton(onClick = { message = null; confirmUndo = true }) { Text("撤销本次恢复") } }
            } else null)
    }
}
