package com.bilibili.watchlater.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.watchlater.data.remote.BiliApiService
import com.bilibili.watchlater.data.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class BackupPreview(val creatorCount: Int, val recordCount: Int, val historyWeeks: Int, val browserCheckerCount: Int)

data class HomeUiState(
    val isChecking: Boolean = false,
    val isCancelling: Boolean = false,
    val progress: Float = 0f,
    val progressText: String = "",
    val trackingCount: Int = 0,
    val lastNewCount: Int = 0,
    val lastCheck: Long = 0,
    val totalAdded: Int = 0,
    val newVideoWindowHours: Int = 24,
    val historyWeeks: Int = 3,
    val themeMode: String = "system",
    val sortMode: String = "default",
    val report: CheckReport? = null,
    val addDialogVisible: Boolean = false,
    val addError: String? = null,
    val searchQuery: String = "",
    val busyLabel: String? = null,
    val message: String? = null,
    val backupPreview: BackupPreview? = null
) {
    val isBusy: Boolean get() = isChecking || busyLabel != null
}

class HomeViewModel(
    private val checkRepo: CheckRepository,
    private val creatorRepo: CreatorRepository,
    private val settingsRepo: SettingsRepository,
    private val api: BiliApiService,
    private val activityRepo: ActivityRepository,
    private val backupRepo: BackupRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    private var pendingBackup: ConfigurationBackup? = null

    val creators = creatorRepo.allCreators.map { list ->
        list.map { CreatorUi(it.mid, it.name, it.face, it.fans, it.lastPubdate) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val publications = activityRepo.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        reloadSettings()
        viewModelScope.launch { creators.collect { list -> _uiState.update { it.copy(trackingCount = list.size) } } }
        viewModelScope.launch {
            try { activityRepo.prune() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { showMessage("清理旧更新记录失败：${error.message}") }
        }
    }

    private fun reloadSettings() {
        _uiState.update { it.copy(
            totalAdded = settingsRepo.totalAdded, lastCheck = settingsRepo.lastCheck,
            lastNewCount = settingsRepo.lastNewCount, newVideoWindowHours = settingsRepo.newVideoWindowHours,
            historyWeeks = settingsRepo.historyWeeks, themeMode = settingsRepo.themeMode, sortMode = settingsRepo.sortMode
        ) }
    }

    fun showMessage(text: String) { _uiState.update { it.copy(message = text) } }
    fun dismissMessage() { _uiState.update { it.copy(message = null) } }
    fun setSearchQuery(query: String) { _uiState.update { it.copy(searchQuery = query) } }
    fun showAddDialog() { if (!uiState.value.isBusy) _uiState.update { it.copy(addDialogVisible = true, addError = null) } }
    fun hideAddDialog() { if (!uiState.value.isBusy) _uiState.update { it.copy(addDialogVisible = false, addError = null) } }

    private fun operation(label: String, block: suspend () -> Unit) {
        if (uiState.value.isBusy) return
        _uiState.update { it.copy(busyLabel = label) }
        viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { showMessage("$label 失败：${error.message ?: "请稍后重试"}") }
            finally { _uiState.update { it.copy(busyLabel = null) } }
        }
    }

    fun addCreator(input: String) {
        val text = input.trim()
        val id = Regex("space\\.bilibili\\.com/(\\d+)").find(text)?.groupValues?.get(1) ?: text
        val mid = id.takeIf { it.matches(Regex("[0-9]+")) }?.toLongOrNull()?.takeIf { it > 0 }
        if (mid == null) { _uiState.update { it.copy(addError = "请输入有效 UID 或 B站主页链接") }; return }
        operation("添加 UP 主") {
            try {
                if (creatorRepo.exists(mid)) { _uiState.update { it.copy(addError = "这位 UP 主已在追踪名单中") }; return@operation }
                val info = api.getUpInfo(mid)
                creatorRepo.add(mid, info.name, info.face, info.fans)
                _uiState.update { it.copy(addDialogVisible = false, addError = null) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _uiState.update { it.copy(addError = "添加失败，请检查网络或 UID") } }
        }
    }

    fun removeCreator(mid: Long) = operation("移除 UP 主") { creatorRepo.remove(mid) }

    fun startCheck() {
        if (uiState.value.isBusy) return
        _uiState.update { it.copy(isChecking = true, isCancelling = false, progress = 0f, progressText = "正在检查…", report = null) }
        viewModelScope.launch {
            try {
                val report = checkRepo.checkForNewVideos { current, total, name ->
                    _uiState.update { it.copy(progress = if (total > 0) current.toFloat() / total else 0f,
                        progressText = "${current}/${total} · $name") }
                }
                reloadSettings()
                _uiState.update { it.copy(report = report) }
                showMessage(when (report.status) {
                    "empty_list" -> "追踪名单为空，请先添加或导入 UP 主"
                    "cancelled" -> "检查已取消，已添加 ${report.added} 个视频"
                    else -> "检查完成：添加 ${report.added} 个视频" + if (report.errors > 0) "，${report.errors} 位 UP 主检查失败" else ""
                })
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { showMessage("检查失败：${error.message ?: "请检查网络或重新登录"}") }
            finally { _uiState.update { it.copy(isChecking = false, isCancelling = false) } }
        }
    }

    fun cancelCheck() {
        if (!uiState.value.isChecking) return
        checkRepo.cancel()
        _uiState.update { it.copy(isCancelling = true) }
    }

    fun setSortMode(mode: String) {
        if (uiState.value.isBusy || mode !in SettingsRepository.SORT_MODES) return
        settingsRepo.sortMode = mode; reloadSettings()
    }
    fun setNewVideoWindow(hours: Int) {
        if (uiState.value.isBusy || hours !in SettingsRepository.WINDOW_HOURS) return
        settingsRepo.newVideoWindowHours = hours; reloadSettings()
    }
    fun setThemeMode(mode: String) {
        if (uiState.value.isBusy || mode !in SettingsRepository.THEME_MODES) return
        settingsRepo.themeMode = mode; reloadSettings()
    }
    fun setHistoryWeeks(weeks: Int) {
        if (weeks !in 1..52) { showMessage("请输入 1–52 周"); return }
        operation("保存保留周数") {
            activityRepo.prune(weeks)
            settingsRepo.historyWeeks = weeks; reloadSettings()
            showMessage("已保存：最近 $weeks 周（含本周）")
        }
    }

    fun reimportFollows(uid: Long) {
        if (uid <= 0) { showMessage("请重新登录 B站后再导入关注"); return }
        operation("导入关注") {
            val result = checkRepo.importFollowList(uid) { _, imported, total ->
                _uiState.update { it.copy(busyLabel = "导入关注 · $imported / $total") }
            }
            showMessage("导入${if (result.status == "ok") "完成" else "中断"}：新增 ${result.imported} 位 UP 主")
        }
    }

    fun exportTrackingList(uri: Uri, context: Context) = operation("导出名单") {
        withContext(Dispatchers.IO) { writeFile(uri, context, backupRepo.exportTrackingList()) }
        showMessage("追踪名单已保存")
    }
    fun importTrackingList(uri: Uri, context: Context) = operation("导入名单") {
        val creators = withContext(Dispatchers.IO) { backupRepo.parseTrackingList(readFile(uri, context)) }
        val result = creatorRepo.importCreators(creators)
        settingsRepo.importDone = true
        showMessage("新增 ${result.imported} 位 UP 主，跳过 ${result.skipped} 位已有 UP 主")
    }
    fun exportConfiguration(uri: Uri, context: Context) = operation("导出备份") {
        withContext(Dispatchers.IO) { writeFile(uri, context, backupRepo.exportConfiguration()) }
        showMessage("配置备份已保存")
    }
    fun prepareConfigurationImport(uri: Uri, context: Context) = operation("读取备份") {
        val backup = withContext(Dispatchers.IO) { backupRepo.parseConfiguration(readFile(uri, context)) }
        pendingBackup = backup
        _uiState.update { it.copy(backupPreview = BackupPreview(backup.creators.size, backup.publications.size,
            backup.configuration.historyWeeks, backup.browserCheckerCount)) }
    }
    fun cancelConfigurationImport() {
        if (uiState.value.isBusy) return
        pendingBackup = null; _uiState.update { it.copy(backupPreview = null) }
    }
    fun confirmConfigurationImport() {
        val backup = pendingBackup ?: return
        operation("恢复配置") {
            backupRepo.restore(backup)
            pendingBackup = null; reloadSettings()
            _uiState.update { it.copy(backupPreview = null, searchQuery = "") }
            showMessage("配置已恢复")
        }
    }

    private fun readFile(uri: Uri, context: Context): String {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= BackupRepository.MAX_BYTES) { "文件不能超过 25 MB" }
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name()).trimStart('\uFEFF')
        } ?: error("无法读取文件")
    }
    private fun writeFile(uri: Uri, context: Context, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= BackupRepository.MAX_BYTES) { "备份不能超过 25 MB，请缩短记录保留周数" }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("无法保存文件")
    }

    override fun onCleared() { checkRepo.cancel(); super.onCleared() }
}
