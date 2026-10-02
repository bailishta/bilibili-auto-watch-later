package com.bilibili.watchlater.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen(state: HomeUiState, onCheck: () -> Unit, onOpenWatchLater: () -> Unit, onList: () -> Unit,
                    onActivity: () -> Unit, onSettings: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("追踪 UP 主", state.trackingCount, Modifier.weight(1f))
                    StatTile("本次新发现", state.lastNewCount, Modifier.weight(1f))
                }
                Button(onClick = onCheck, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                    Text(if (state.isChecking) "正在检查…" else "立即检查", fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(WindowOptions[state.newVideoWindowHours].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onSettings) { Text("检查范围") }
                }
                OutlinedButton(onClick = onOpenWatchLater, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("打开 B站 APP · 稍后再看") }
            }
        }
        item {
            Panel {
                PanelTitle("详细管理")
                Text("管理追踪名单、查看更新记录和备份配置", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onList, modifier = Modifier.weight(1f)) { Text("追踪名单") }
                    OutlinedButton(onClick = onActivity, modifier = Modifier.weight(1f)) { Text("更新分布") }
                }
            }
        }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("累计添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${state.totalAdded} 个视频", fontWeight = FontWeight.SemiBold)
                }
                Text("上次检查 · ${formatCheck(state.lastCheck)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.report?.let { report ->
            if (report.newVideos.isNotEmpty()) item {
                Panel {
                    PanelTitle("本次已添加")
                    report.newVideos.take(8).forEach { video ->
                        Text("${video.author} · ${video.title}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    if (report.newVideos.size > 8) Text("共 ${report.newVideos.size} 个视频，可在稍后观看查看全部", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier) {
    Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
