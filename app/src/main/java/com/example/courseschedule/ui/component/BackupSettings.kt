package com.example.courseschedule.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.courseschedule.data.db.ScheduleBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupSettings(
    captureBackup: suspend () -> ScheduleBackup,
    restoreBackup: suspend (ScheduleBackup) -> Unit
) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ScheduleBackup?>(null) }

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
            }
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) perform("正在导出备份") {
            withContext(Dispatchers.IO) {
                val backup = captureBackup()
                val output = resolver.openOutputStream(uri, "wt") ?: error("无法写入备份文件")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(backup.encode()) }
            }
            message = "备份已导出"
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
            Text("备份与恢复", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { export.launch("CourseSchedule-backup.json") }) { Text("导出备份") }
            TextButton(onClick = { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                Text("从文件恢复")
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
                    Text("${backup.courseCount} 门课程 · ${backup.examCount} 场考试 · ${backup.changeCount} 条调课")
                    Spacer(Modifier.height(8.dp))
                    Text("恢复会替换设备上的全部学期及课表数据。")
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
    message?.let { result ->
        AlertDialog(onDismissRequest = { message = null }, text = { Text(result) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("确定") } })
    }
}
