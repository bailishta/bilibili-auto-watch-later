package com.bilibili.watchlater.ui.log

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilibili.watchlater.ui.theme.BgGray
import com.bilibili.watchlater.ui.theme.BgWhite
import com.bilibili.watchlater.ui.theme.Pink
import com.bilibili.watchlater.ui.theme.TextHint
import com.bilibili.watchlater.ui.theme.TextPrimary
import com.bilibili.watchlater.util.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    var showLevel by remember { mutableStateOf<AppLogger.Level?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("运行日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val text = AppLogger.entries.joinToString("\n") { it.formatted() }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, "导出日志"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "导出日志")
                    }
                    IconButton(onClick = { AppLogger.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "清空日志")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Pink)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BgGray)
        ) {
            // 过滤器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(BgWhite)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                FilterChip(
                    selected = showLevel == null,
                    onClick = { showLevel = null },
                    label = { Text("全部", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Pink.copy(alpha = 0.2f)
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                AppLogger.Level.entries.forEach { level ->
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = showLevel == level,
                        onClick = { showLevel = if (showLevel == level) null else level },
                        label = {
                            Text(
                                text = when (level) {
                                    AppLogger.Level.DEBUG -> "DEBUG"
                                    AppLogger.Level.INFO -> "INFO"
                                    AppLogger.Level.WARN -> "WARN"
                                    AppLogger.Level.ERROR -> "ERROR"
                                },
                                fontSize = 12.sp,
                                color = levelColor(level)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Pink.copy(alpha = 0.2f)
                        )
                    )
                }
            }

            // 日志列表
            val logs = AppLogger.entries
                .let { if (showLevel != null) it.filter { e -> e.level == showLevel } else it }
                .reversed()

            if (logs.isEmpty()) {
                Text(
                    text = "暂无日志",
                    color = TextHint,
                    modifier = Modifier.padding(16.dp)
                )
            }

            val listState = rememberLazyListState()
            LazyColumn(state = listState) {
                items(logs) { entry ->
                    LogItem(entry)
                }
            }
        }
    }
}

@Composable
private fun LogItem(entry: AppLogger.Entry) {
    val bgColor = when (entry.level) {
        AppLogger.Level.ERROR -> Color(0x1AFF0000)
        AppLogger.Level.WARN -> Color(0x1AFF9800)
        else -> Color.Transparent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = entry.formatted(),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = levelColor(entry.level),
            lineHeight = 16.sp
        )
        if (entry.throwable != null) {
            Text(
                text = entry.throwable.stackTraceToString(),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextHint,
                lineHeight = 14.sp,
                maxLines = 20
            )
        }
        Spacer(modifier = Modifier.height(1.dp))
    }
}

private fun levelColor(level: AppLogger.Level): Color = when (level) {
    AppLogger.Level.DEBUG -> TextHint
    AppLogger.Level.INFO -> TextPrimary
    AppLogger.Level.WARN -> Color(0xFFFF9800)
    AppLogger.Level.ERROR -> Color(0xFFFF0000)
}
