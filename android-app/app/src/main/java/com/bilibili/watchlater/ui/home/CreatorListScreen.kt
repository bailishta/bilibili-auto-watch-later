package com.bilibili.watchlater.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bilibili.watchlater.ui.components.CreatorAvatar

@Composable
fun CreatorListScreen(state: HomeUiState, creators: List<CreatorUi>, viewModel: HomeViewModel, onImport: () -> Unit,
                      onExport: () -> Unit, onOpenCreator: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<CreatorUi?>(null) }
    val filtered = remember(creators, state.searchQuery, state.sortMode) {
        val query = state.searchQuery.trim()
        sortCreators(creators.filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) || it.mid.toString().contains(query) }, state.sortMode)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PanelTitle("追踪名单 · ${creators.size}")
                FilledTonalButton(onClick = viewModel::showAddDialog, enabled = !state.isBusy,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("添加")
                }
            }
        }
        item {
            OutlinedTextField(value = state.searchQuery, onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("搜索名称或 UID") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (state.searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.setSearchQuery("") }) { Icon(Icons.Default.Close, "清空搜索") } },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { expanded = true }, enabled = !state.isBusy) { Text(SortOptions[state.sortMode].orEmpty()) }
                    DropdownMenu(expanded, { expanded = false }) {
                        SortOptions.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { viewModel.setSortMode(key); expanded = false }) }
                    }
                }
                TextButton(onClick = onExport, enabled = !state.isBusy && creators.isNotEmpty()) { Text("导出") }
                TextButton(onClick = onImport, enabled = !state.isBusy) { Text("导入") }
            }
        }
        if (filtered.isEmpty()) item {
            Panel {
                Text(if (state.searchQuery.isBlank()) "还没有追踪 UP 主" else "没有匹配的 UP 主", style = MaterialTheme.typography.titleMedium)
                Text("可使用右上角添加，或在设置中导入 B站关注", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(filtered, key = { it.mid }) { creator ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CreatorAvatar(creator.name, creator.face, Modifier.size(38.dp).clickable { onOpenCreator(creator.mid) })
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable { onOpenCreator(creator.mid) }) {
                        Text(creator.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("UID ${creator.mid}" + (creator.fans?.let { " · 粉丝 ${formatFans(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("更新 · ${formatPublication(creator.lastPubdate)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    IconButton(onClick = { removing = creator }, enabled = !state.isBusy) { Icon(Icons.Default.Close, "移除${creator.name}", Modifier.size(18.dp)) }
                }
            }
        }
    }
    removing?.let { creator ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("移除 UP 主") },
            text = { Text("将 ${creator.name} 移出追踪名单？其更新记录也会移除。") },
            confirmButton = { TextButton(onClick = { viewModel.removeCreator(creator.mid); removing = null }, enabled = !state.isBusy) { Text("移除") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } })
    }
}
