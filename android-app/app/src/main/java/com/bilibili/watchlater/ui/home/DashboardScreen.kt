package com.bilibili.watchlater.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilibili.watchlater.ui.theme.ActionCoral
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.graphics.Color
import com.bilibili.watchlater.ui.theme.OnCoral

@Composable
fun DashboardScreen(state: HomeUiState, onCheck: () -> Unit, onOpenWatchLater: () -> Unit, onSettings: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("把想看的留好", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("喜欢的 UP 主，更新不落下", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("追踪 UP 主", state.trackingCount, Modifier.weight(1f))
                    StatTile("本次新发现", state.lastNewCount, Modifier.weight(1f))
                }
                Button(onClick = onCheck, enabled = !state.isChecking && state.busyLabel == null, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = ActionCoral, contentColor = OnCoral,
                        disabledContainerColor = if (state.isChecking) ActionCoral else MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = if (state.isChecking) OnCoral else MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!state.isChecking) Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                            val count = state.progressText.substringBefore(" · ").takeIf { '/' in it }
                            Text(if (state.isChecking) (if (state.isCancelling) "正在取消…" else "检查中… ${count.orEmpty()}".trimEnd()) else if (state.isCheckPaused) "继续检查" else "立即检查",
                                fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        if (state.isChecking) LinearProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Color.White, trackColor = Color.White.copy(alpha = .25f), gapSize = 0.dp, drawStopIndicator = {})
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(WindowOptions[state.newVideoWindowHours].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onSettings) { Text("检查范围") }
                }
                OutlinedButton(onClick = onOpenWatchLater, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("打开 B站 APP · 稍后再看")
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
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(value.toString(), fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                color = if (label == "本次新发现") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}
