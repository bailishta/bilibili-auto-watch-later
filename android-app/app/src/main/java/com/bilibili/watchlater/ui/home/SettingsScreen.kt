package com.bilibili.watchlater.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: HomeUiState, viewModel: HomeViewModel, onBackupExport: () -> Unit, onBackupImport: () -> Unit,
                   onReimport: () -> Unit, onLogout: () -> Unit, onLog: () -> Unit) {
    var windowMenu by remember { mutableStateOf(false) }
    var weeks by rememberSaveable(state.historyWeeks) { mutableStateOf(state.historyWeeks.toString()) }
    var confirmLogout by remember { mutableStateOf(false) }
    val validWeeks = weeks.toIntOrNull()?.takeIf { it in 1..52 }
    LaunchedEffect(state.isBusy) { if (state.isBusy) windowMenu = false }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Panel {
                PanelTitle("手动检查范围")
                Text("检查时将此范围内未观看的新视频加入稍后观看", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(
                    expanded = windowMenu,
                    onExpandedChange = { if (!state.isBusy) windowMenu = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = WindowOptions[state.newVideoWindowHours].orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        enabled = !state.isBusy,
                        singleLine = true,
                        label = { Text("视频时间范围") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = windowMenu) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !state.isBusy)
                    )
                    ExposedDropdownMenu(expanded = windowMenu, onDismissRequest = { windowMenu = false }) {
                        WindowOptions.forEach { (hours, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { viewModel.setNewVideoWindow(hours); windowMenu = false }) }
                    }
                }
            }
        }
        item {
            Panel {
                PanelTitle("更新记录")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = weeks, onValueChange = { value -> weeks = value.filter { it.isDigit() }.take(2) }, modifier = Modifier.weight(1f),
                        label = { Text("保留最近几周") }, suffix = { Text("周") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = validWeeks == null, enabled = !state.isBusy)
                    Button(onClick = { validWeeks?.let(viewModel::setHistoryWeeks) }, enabled = !state.isBusy && validWeeks != null) { Text("保存") }
                }
                Text("默认最近 3 个自然周（含本周），可设 1–52 周。缩短会清理旧记录，延长后逐步积累，每位 UP 主最多 500 条。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Panel {
                PanelTitle("显示模式")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (key, label) ->
                        FilterChip(selected = state.themeMode == key, onClick = { viewModel.setThemeMode(key) }, label = { Text(label) }, enabled = !state.isBusy)
                    }
                }
            }
        }
        item {
            Panel {
                PanelTitle("配置备份")
                Text("保存检查范围、排序、显示设置、追踪名单和已采集记录。备份不包含登录信息。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBackupExport, enabled = !state.isBusy, modifier = Modifier.weight(1f)) { Text("导出备份") }
                    OutlinedButton(onClick = onBackupImport, enabled = !state.isBusy, modifier = Modifier.weight(1f)) { Text("导入备份") }
                }
                Text("可读取插件备份中的名单和设置。安卓备份不含浏览器排程；名单互通可使用名单页的导入／导出。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        item {
            Panel {
                PanelTitle("账号与工具")
                OutlinedButton(onClick = onReimport, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) { Text("重新导入 B站关注") }
                OutlinedButton(onClick = onLog, modifier = Modifier.fillMaxWidth()) { Text("运行日志") }
                TextButton(onClick = { confirmLogout = true }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) { Text("退出登录") }
            }
        }
        item {
            Text("稍后观看助手 · Android 2.0.0\n字体跟随 Android 系统，界面支持系统字号。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("退出登录") },
        text = { Text("退出 B站账号？追踪名单与本地更新记录会保留。") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }, enabled = !state.isBusy) { Text("退出") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } })
}
