package com.bilibili.watchlater.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilibili.watchlater.ui.components.AddCreatorDialog
import com.bilibili.watchlater.util.BiliAppLauncher
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onLogout: () -> Unit, onOpenLog: () -> Unit = {}, userId: Long = 0) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val creators by viewModel.creators.collectAsStateWithLifecycle()
    val records by viewModel.publications.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val listImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.importTrackingList(it, context) } }
    val configImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.prepareConfigurationImport(it, context) } }
    val listExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { viewModel.exportTrackingList(it, context) } }
    val configExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { viewModel.exportConfiguration(it, context) } }
    val mimeTypes = arrayOf("application/json", "text/plain", "application/octet-stream")
    val tabs = listOf("首页", "名单", "更新", "设置")
    val icons = listOf(Icons.Default.Home, Icons.Default.People, Icons.Default.BarChart, Icons.Default.Settings)
    fun openUrl(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (error: Exception) { viewModel.showMessage("无法打开链接，请安装可用的浏览器") }
    }
    fun openWatchLater() {
        BiliAppLauncher.openWatchLater(context)?.let(viewModel::showMessage)
    }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbar.showSnackbar(message)
            if (viewModel.uiState.value.message == message) viewModel.dismissMessage()
        }
    }
    BackHandler(page != 0) { page = 0 }

    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = { Text(if (page == 0) "稍后观看助手" else tabs[page], fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = { Text("已登录", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 18.dp)) })
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(selected = page == index, onClick = { page = index }, label = { Text(label) },
                        icon = { Icon(icons[index], contentDescription = null) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer))
                }
            }
        }, snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isBusy) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (state.isChecking) (if (state.isCancelling) "正在取消…" else state.progressText) else state.busyLabel.orEmpty(),
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            if (state.isChecking) TextButton(onClick = viewModel::cancelCheck, enabled = !state.isCancelling) { Text("取消") }
                        }
                        if (state.isChecking) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            AnimatedContent(targetState = page, modifier = Modifier.weight(1f), label = "页面切换",
                transitionSpec = {
                    (fadeIn(tween(250, easing = FastOutSlowInEasing)) + slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it / 30 })
                        .togetherWith(fadeOut(tween(150)))
                }) { selected ->
                holder.SaveableStateProvider(selected) {
                    when (selected) {
                        0 -> DashboardScreen(state, viewModel::startCheck, ::openWatchLater, { page = 1 }, { page = 2 }, { page = 3 })
                        1 -> CreatorListScreen(state, creators, viewModel, { listImport.launch(mimeTypes) },
                            { listExport.launch("bilibili-tracking-list-${LocalDate.now()}.json") }, { openUrl("https://space.bilibili.com/$it") })
                        2 -> ActivityScreen(creators, records, state.historyWeeks) { page = 3 }
                        else -> SettingsScreen(state, viewModel, { configExport.launch("bilibili-configuration-android-${LocalDate.now()}.json") },
                            { configImport.launch(mimeTypes) }, { viewModel.reimportFollows(userId) }, onLogout, onOpenLog)
                    }
                }
            }
        }
    }
    if (state.addDialogVisible) AddCreatorDialog(error = state.addError, onConfirm = viewModel::addCreator, onDismiss = viewModel::hideAddDialog, busy = state.isBusy)
    state.backupPreview?.let { preview ->
        AlertDialog(onDismissRequest = viewModel::cancelConfigurationImport, title = { Text("恢复配置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${preview.creatorCount} 位 UP 主 · ${preview.recordCount} 条更新记录\n保留最近 ${preview.historyWeeks} 周")
                    Text("将替换当前名单和设置，保留本机已处理视频及统计信息。", style = MaterialTheme.typography.bodySmall)
                    if (preview.browserCheckerCount > 0) Text("此备份包含浏览器排程，安卓仅恢复名单和设置。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }, confirmButton = { TextButton(onClick = viewModel::confirmConfigurationImport, enabled = !state.isBusy) { Text("导入并替换") } },
            dismissButton = { TextButton(onClick = viewModel::cancelConfigurationImport, enabled = !state.isBusy) { Text("取消") } })
    }
}
