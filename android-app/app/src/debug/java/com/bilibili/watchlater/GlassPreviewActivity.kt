package com.bilibili.watchlater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.bilibili.watchlater.ui.home.*
import com.bilibili.watchlater.ui.theme.*
import com.bilibili.watchlater.ui.components.AddCreatorDialog

/** Debug-only optical test scene; no account, networking, or production preference writes. */
class GlassPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var dark by remember { mutableStateOf(intent.getBooleanExtra("dark", false)) }
            var glass by remember { mutableStateOf(true) }
            var selected by remember { mutableIntStateOf(intent.getIntExtra("page", 0)) }
            var dialog by remember { mutableStateOf(false) }
            var search by remember { mutableStateOf("") }
            var menu by remember { mutableStateOf(false) }
            var choice by remember { mutableIntStateOf(0) }
            val labels = listOf("首页", "名单", "更新", "设置")
            val icons = listOf(Icons.Default.Home, Icons.Default.People, Icons.Default.BarChart, Icons.Default.Settings)
            BiliWatchLaterTheme(darkTheme = dark, liquidGlass = glass) {
                if (selected == 4) {
                    OpticsPreview()
                    return@BiliWatchLaterTheme
                }
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalPageInsets provides PaddingValues(top = 100.dp, bottom = 110.dp)) {
                    Box(Modifier.fillMaxSize().captureGlassBackdrop()) {
                    when (selected) {
                    0 -> DashboardScreen(HomeUiState(trackingCount = 37, lastNewCount = 5, totalAdded = 128),
                        onCheck = { dialog = true }, onOpenWatchLater = {}, onSettings = { selected = 3 })
                    2 -> ActivityScreen(emptyList(), emptyList(), 3)
                    else -> LazyColumn(Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 112.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Panel {
                                PanelTitle("液态玻璃 · ${labels[selected]}")
                                Text("普通面板与表单 · 仅底部导航使用玻璃")
                                AppOutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(),
                                    label = { Text("搜索名称或 UID") }, singleLine = true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppButton(onClick = { dialog = true }) { Text("添加 UP 主") }
                                    Box {
                                        AppOutlinedButton(onClick = { menu = true }) { Text("排序菜单") }
                                        DropdownMenu(menu, { menu = false },
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                                            listOf("默认顺序", "按粉丝数量", "按更新时间").forEach { label ->
                                                DropdownMenuItem(text = { Text(label) }, onClick = { menu = false })
                                            }
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("跟随系统", "浅色", "深色").forEachIndexed { index, label ->
                                        AppFilterChip(choice == index, { choice = index }, { Text(label) })
                                    }
                                }
                                if (selected == 3) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("液态玻璃", Modifier.weight(1f))
                                        AppSwitch(glass, { glass = it })
                                    }
                                    AppTextButton(onClick = { dark = !dark }) { Text("切换明暗") }
                                }
                                AppOutlinedButton(onClick = {}, enabled = false) { Text("检查中不可操作") }
                            }
                        }
                        items((1..30).toList()) { index ->
                            val colors = listOf(Color(0xFFDF536F), Color(0xFF387BE5), Color(0xFF27A987), Color(0xFFEAAF35))
                            AppSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Row(Modifier.fillMaxWidth().height(88.dp)
                                .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(56.dp).background(colors[index % colors.size], RoundedCornerShape(12.dp)))
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text("内容 $index · 稍后慢慢看", color = MaterialTheme.colorScheme.onSurface)
                                    Text("真实页面背景 / REAL CONTENT", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            }
                        }
                    }
                    }
                    }
                    }
                    if (dialog) AddCreatorDialog(error = null, onConfirm = { dialog = false }, onDismiss = { dialog = false })
                    if (glass) GlassNavigationBar(selected, labels, icons, { selected = it },
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp))
                    else NavigationBar(Modifier.align(Alignment.BottomCenter)) {
                        labels.forEachIndexed { index, label -> NavigationBarItem(selected == index, { selected = index },
                            icon = { Icon(icons[index], null) }, label = { Text(label) }) }
                    }
                }
            }
        }
    }
}

/** High-contrast geometry makes actual refraction distinguishable from painted borders. */
@Composable
private fun OpticsPreview() {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().captureGlassBackdrop()) {
            val cell = 80.dp.toPx()
            for (y in 0..(size.height / cell).toInt()) {
                for (x in 0..(size.width / cell).toInt()) {
                    drawRect(if ((x + y) % 2 == 0) Color(0xFFF1F3F6) else Color(0xFF48576F),
                        Offset(x * cell, y * cell), Size(cell, cell))
                }
            }
        }
        Box(Modifier.align(Alignment.Center).size(280.dp, 200.dp)
            .liquidGlass(RoundedCornerShape(36.dp)))
        Text("光学检查 · 磨砂透色与边缘折射",
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(20.dp),
            style = MaterialTheme.typography.bodyMedium)
    }
}
