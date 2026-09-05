package com.example.courseschedule.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.courseschedule.util.SettingsPrefs
import com.example.courseschedule.util.VersionChecker

private sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class Result(val latest: String, val hasUpdate: Boolean) : UpdateCheckState
    data object Failed : UpdateCheckState
}

/**
 * 全局设置页面：字体大小、学期管理、清理课程数据、检查更新。
 * 通过 onOpenSemester/onClearAll 回调与宿主交互。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    versionName: String,
    onBack: () -> Unit,
    onOpenSemesterSetup: () -> Unit,
    onClearAll: () -> Unit
) {
    val outerDensity = LocalDensity.current
    // 全局已应用的字体倍率（含系统 + 存储值）
    val storedScale by SettingsPrefs.fontScale.collectAsStateWithLifecycle()
    // 拖动中的本地预览值：仅覆盖本页，避免每帧写全局触发全 App 重排
    var fontScale by remember { mutableFloatStateOf(SettingsPrefs.getFontScale()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var checkState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
    val scope = rememberCoroutineScope()

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("\u6e05\u9664\u6240\u6709\u8bfe\u7a0b") },
            text = { Text("\u6b64\u64cd\u4f5c\u5c06\u5220\u9664\u5168\u90e8\u8bfe\u7a0b\u3001\u8bfe\u8868\u3001\u8003\u8bd5\u548c\u6559\u5ba4\u4fe1\u606f\uff08\u4fdd\u7559\u5b66\u671f\u8bbe\u7f6e\uff09\uff0c\u4e0d\u53ef\u64a4\u9500\u3002\u786e\u8ba4\u7ee7\u7eed\uff1f") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearAll()
                }) { Text("\u6e05\u9664", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("\u53d6\u6d88") }
            }
        )
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = outerDensity.density,
            fontScale = outerDensity.fontScale / storedScale.coerceAtLeast(0.1f) * fontScale
        )
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u8bbe\u7f6e", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 字体大小 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("\u5b57\u4f53\u5927\u5c0f", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${(fontScale * 100).toInt()}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = fontScale,
                        // 拖动仅更新本地预览；松手一次提交全局，避免每帧全局 Density 重排
                        onValueChange = { fontScale = it },
                        onValueChangeFinished = { SettingsPrefs.setFontScale(fontScale) },
                        valueRange = 0.8f..1.4f,
                        steps = 5
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("\u5c0f", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("\u6807\u51c6", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("\u5927", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── 学期管理 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSemesterSetup).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("\u5b66\u671f\u7ba1\u7406", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("\u5f00\u5b66\u65e5\u671f\u3001\u603b\u5468\u6570\u3001\u8282\u6570\u4e0e\u65f6\u95f4\u8bbe\u7f6e", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── 检查更新 + 版本号 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\u7248\u672c", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("v$versionName", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = {
                            checkState = UpdateCheckState.Checking
                            scope.launch {
                                val latest = withContext(Dispatchers.IO) {
                                    VersionChecker.fetchLatestVersion()
                                }
                                checkState = if (latest == null) {
                                    UpdateCheckState.Failed
                                } else {
                                    UpdateCheckState.Result(latest, VersionChecker.isNewer(latest, versionName))
                                }
                            }
                        },
                        enabled = checkState !is UpdateCheckState.Checking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (checkState is UpdateCheckState.Checking) "\u68c0\u67e5\u4e2d..." else "\u68c0\u67e5\u66f4\u65b0", fontSize = 14.sp)
                    }
                    when (val s = checkState) {
                        is UpdateCheckState.Result -> Text(
                            if (s.hasUpdate) {
                                "\u53d1\u73b0\u65b0\u7248\u672c ${s.latest}\uff0c\u8bf7\u5230 GitHub \u4e0b\u8f7d\u5b89\u88c5"
                            } else "\u5f53\u524d\u5df2\u662f\u6700\u65b0\u7248\u672c",
                            fontSize = 12.sp,
                            color = if (s.hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        UpdateCheckState.Failed -> Text(
                            "\u68c0\u67e5\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> {}
                    }
                }
            }

            // ── 清理数据 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = { showClearConfirm = true }).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("\u6e05\u9664\u6240\u6709\u8bfe\u7a0b\u4fe1\u606f", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                        Text("\u5220\u9664\u8bfe\u7a0b\u3001\u8bfe\u8868\u3001\u8003\u8bd5\u4e0e\u6559\u5ba4\uff08\u4fdd\u7559\u5b66\u671f\uff09", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    }
}
