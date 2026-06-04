package com.bilibili.watchlater.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.bilibili.watchlater.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilibili.watchlater.ui.components.AddCreatorDialog
import com.bilibili.watchlater.ui.theme.BgGray
import com.bilibili.watchlater.ui.theme.BgWhite
import com.bilibili.watchlater.ui.theme.LocalDeviceScale
import com.bilibili.watchlater.ui.theme.Pink
import com.bilibili.watchlater.ui.theme.PinkBg
import com.bilibili.watchlater.ui.theme.TextHint
import com.bilibili.watchlater.ui.theme.TextPrimary
import com.bilibili.watchlater.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLogout: () -> Unit,
    onOpenLog: () -> Unit = {}
) {
    val scale = LocalDeviceScale.current
    val uiState by viewModel.uiState.collectAsState()
    val creators by viewModel.creators.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Pink),
                actions = {
                    Text(
                        text = "日志",
                        color = BgWhite,
                        fontSize = (13 * scale.fontScale).sp,
                        modifier = Modifier
                            .clickable { onOpenLog() }
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.logout),
                        color = BgWhite,
                        fontSize = (13 * scale.fontScale).sp,
                        modifier = Modifier
                            .clickable { onLogout() }
                            .padding(end = (16 * scale.spacingScale).dp)
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BgGray)
        ) {
            // 统计卡片
            item {
                StatsCard(uiState)
            }

            // 手动检查按钮 + 新视频窗口
            item {
                CheckSection(uiState, viewModel)
            }

            // 检查进度
            if (uiState.isChecking) {
                item {
                    ProgressSection(uiState, viewModel::cancelCheck)
                }
            }

            // 追踪名单标题
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = (16 * scale.spacingScale).dp,
                            vertical = (12 * scale.spacingScale).dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.tracking_list)} (${creators.size})",
                        fontSize = (16 * scale.fontScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Button(
                        onClick = { viewModel.showAddDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = Pink),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size((18 * scale.spacingScale).dp)
                        )
                        Spacer(modifier = Modifier.width((4 * scale.spacingScale).dp))
                        Text(stringResource(R.string.add_creator), fontSize = (13 * scale.fontScale).sp)
                    }
                }
            }

            // 搜索框
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("搜索UP主名称或UID", fontSize = (13 * scale.fontScale).sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextHint)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = (16 * scale.spacingScale).dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BgWhite,
                        unfocusedContainerColor = BgWhite,
                        focusedBorderColor = Pink,
                        unfocusedBorderColor = PinkBg
                    ),
                    shape = RoundedCornerShape((8 * scale.spacingScale).dp)
                )
            }

            // 追踪名单列表（过滤后）
            val q = uiState.searchQuery.trim().lowercase()
            val filtered = if (q.isEmpty()) creators
                else creators.filter { it.mid.toString().contains(q) || it.name.lowercase().contains(q) }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = if (q.isNotEmpty()) "无匹配结果" else stringResource(R.string.no_creators),
                        color = TextHint,
                        fontSize = (14 * scale.fontScale).sp,
                        modifier = Modifier.padding((16 * scale.spacingScale).dp)
                    )
                }
            } else {
                items(filtered, key = { it.mid }) { creator ->
                    CreatorItem(creator, onRemove = { viewModel.removeCreator(creator.mid) })
                }
            }

            // 底部
            item {
                Spacer(modifier = Modifier.height((16 * scale.spacingScale).dp))
                if (uiState.lastCheck > 0) {
                    val fmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()) }
                    Text(
                        text = "${stringResource(R.string.last_check)}${fmt.format(Instant.ofEpochMilli(uiState.lastCheck))}",
                        color = TextHint,
                        fontSize = (12 * scale.fontScale).sp,
                        modifier = Modifier.padding(
                            horizontal = (16 * scale.spacingScale).dp,
                            vertical = (8 * scale.spacingScale).dp
                        )
                    )
                }
            }
        }
    }

    // 添加UP主弹窗
    if (uiState.addDialogVisible) {
        AddCreatorDialog(
            error = uiState.addError,
            onConfirm = { viewModel.addCreator(it) },
            onDismiss = { viewModel.hideAddDialog() }
        )
    }
}

@Composable
private fun StatsCard(uiState: HomeUiState) {
    val scale = LocalDeviceScale.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding((16 * scale.spacingScale).dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        shape = RoundedCornerShape((12 * scale.spacingScale).dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding((20 * scale.spacingScale).dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(value = "${uiState.trackingCount}", label = stringResource(R.string.stats_tracking))
            StatItem(value = "${uiState.lastNewCount}", label = stringResource(R.string.stats_new))
            StatItem(value = "${uiState.totalAdded}", label = stringResource(R.string.stats_total))
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    val scale = LocalDeviceScale.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = (24 * scale.fontScale).sp,
            fontWeight = FontWeight.Bold,
            color = Pink
        )
        Spacer(modifier = Modifier.height((4 * scale.spacingScale).dp))
        Text(
            text = label,
            fontSize = (12 * scale.fontScale).sp,
            color = TextSecondary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckSection(uiState: HomeUiState, viewModel: HomeViewModel) {
    val scale = LocalDeviceScale.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = (16 * scale.spacingScale).dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        shape = RoundedCornerShape((12 * scale.spacingScale).dp)
    ) {
        Column(
            modifier = Modifier.padding((16 * scale.spacingScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { viewModel.startCheck() },
                enabled = !uiState.isChecking,
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width((8 * scale.spacingScale).dp))
                Text(if (uiState.isChecking) stringResource(R.string.checking) else stringResource(R.string.check_now))
            }

            Spacer(modifier = Modifier.height((12 * scale.spacingScale).dp))

            // 新视频时间窗口选择
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.new_video_window),
                    fontSize = (13 * scale.fontScale).sp,
                    color = TextSecondary
                )
                var expanded by remember { mutableStateOf(false) }
                val options = mapOf(
                    12 to "12小时内",
                    24 to "1天内",
                    48 to "2天内",
                    72 to "3天内",
                    168 to "7天内"
                )
                Box {
                    Text(
                        text = options[uiState.newVideoWindowHours] ?: "1天内",
                        fontSize = (13 * scale.fontScale).sp,
                        color = Pink,
                        modifier = Modifier
                            .clickable { expanded = true }
                            .padding(
                                horizontal = (8 * scale.spacingScale).dp,
                                vertical = (4 * scale.spacingScale).dp
                            )
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        options.forEach { (hours, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setNewVideoWindow(hours)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressSection(uiState: HomeUiState, onCancel: () -> Unit) {
    val scale = LocalDeviceScale.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = (16 * scale.spacingScale).dp,
                vertical = (8 * scale.spacingScale).dp
            ),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        shape = RoundedCornerShape((12 * scale.spacingScale).dp)
    ) {
        Column(modifier = Modifier.padding((16 * scale.spacingScale).dp)) {
            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier.fillMaxWidth(),
                color = Pink,
                trackColor = PinkBg
            )
            Spacer(modifier = Modifier.height((8 * scale.spacingScale).dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.progressText,
                    fontSize = (13 * scale.fontScale).sp,
                    color = TextSecondary
                )
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Text(stringResource(R.string.cancel), fontSize = (12 * scale.fontScale).sp)
                }
            }
        }
    }
}

@Composable
private fun CreatorItem(creator: CreatorUi, onRemove: () -> Unit) {
    val scale = LocalDeviceScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgWhite)
            .padding(
                horizontal = (16 * scale.spacingScale).dp,
                vertical = (10 * scale.spacingScale).dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size((40 * scale.spacingScale).dp)
                .clip(CircleShape)
                .background(PinkBg),
            contentAlignment = Alignment.Center
        ) {
            if (creator.face.isNotEmpty()) {
                AsyncImage(
                    model = creator.face,
                    contentDescription = null,
                    modifier = Modifier.size((40 * scale.spacingScale).dp)
                )
            } else {
                Text(
                    text = creator.name.take(1),
                    color = Pink,
                    fontSize = (16 * scale.fontScale).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width((12 * scale.spacingScale).dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = creator.name,
                fontSize = (14 * scale.fontScale).sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "UID: ${creator.mid}",
                fontSize = (12 * scale.fontScale).sp,
                color = TextHint
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = "移除",
                tint = TextHint,
                modifier = Modifier.size((20 * scale.spacingScale).dp)
            )
        }
    }
}
