package com.bilibili.watchlater.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.watchlater.data.local.entity.CreatorEntity
import com.bilibili.watchlater.data.remote.BiliApiService
import com.bilibili.watchlater.data.repository.CheckReport
import com.bilibili.watchlater.data.repository.CheckRepository
import com.bilibili.watchlater.data.repository.CreatorRepository
import com.bilibili.watchlater.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HomeUiState(
    val isChecking: Boolean = false,
    val progress: Float = 0f,
    val progressText: String = "",
    val trackingCount: Int = 0,
    val lastNewCount: Int = 0,
    val lastCheck: Long = 0,
    val totalAdded: Int = 0,
    val newVideoWindowHours: Int = 24,
    val report: CheckReport? = null,
    val addDialogVisible: Boolean = false,
    val addError: String? = null,
    val searchQuery: String = "",
    val importResult: ImportDialogState? = null,
    val exportFile: File? = null
)

data class ImportDialogState(
    val added: Int,
    val skipped: Int,
    val error: String? = null
)

class HomeViewModel(
    private val checkRepo: CheckRepository,
    private val creatorRepo: CreatorRepository,
    private val settingsRepo: SettingsRepository,
    private val api: BiliApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val creators: StateFlow<List<CreatorUi>> = creatorRepo.allCreators
        .map { list -> list.map { CreatorUi(mid = it.mid, name = it.name, face = it.face) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                totalAdded = settingsRepo.totalAdded,
                lastCheck = settingsRepo.lastCheck,
                lastNewCount = settingsRepo.lastNewCount,
                newVideoWindowHours = settingsRepo.newVideoWindowHours
            )
        }
        viewModelScope.launch {
            creators.collect { list ->
                _uiState.value = _uiState.value.copy(trackingCount = list.size)
            }
        }
    }

    fun setNewVideoWindow(hours: Int) {
        settingsRepo.newVideoWindowHours = hours
        _uiState.value = _uiState.value.copy(newVideoWindowHours = hours)
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(addDialogVisible = true, addError = null)
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(addDialogVisible = false, addError = null)
    }

    fun addCreator(input: String) {
        val mid = parseMid(input)
        if (mid == null) {
            _uiState.value = _uiState.value.copy(addError = "请输入有效的UID（纯数字）或B站主页链接")
            return
        }
        viewModelScope.launch {
            try {
                val info = api.getUpInfo(mid)
                creatorRepo.add(info.mid, info.name, info.face)
                _uiState.value = _uiState.value.copy(addDialogVisible = false, addError = null)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "添加UP主失败", e)
                _uiState.value = _uiState.value.copy(addError = "添加失败，请检查网络连接或UID是否正确")
            }
        }
    }

    fun removeCreator(mid: Long) {
        viewModelScope.launch {
            creatorRepo.remove(mid)
        }
    }

    fun startCheck() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true, progress = 0f, progressText = "正在检查...")

            try {
                val report = checkRepo.checkForNewVideos { current, total, name ->
                    _uiState.value = _uiState.value.copy(
                        progress = current.toFloat() / total,
                        progressText = "检查中... $current/$total $name"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    report = report,
                    lastNewCount = report.added,
                    lastCheck = settingsRepo.lastCheck,
                    totalAdded = settingsRepo.totalAdded
                )
            } finally {
                _uiState.value = _uiState.value.copy(isChecking = false)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun cancelCheck() {
        checkRepo.cancel()
        _uiState.value = _uiState.value.copy(isChecking = false)
    }

    fun dismissImportResult() {
        _uiState.value = _uiState.value.copy(importResult = null)
    }

    fun clearExportFile() {
        _uiState.value = _uiState.value.copy(exportFile = null)
    }

    fun exportTrackingList(context: Context) {
        viewModelScope.launch {
            try {
                val all = creatorRepo.getAll()
                if (all.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        importResult = ImportDialogState(added = 0, skipped = 0, error = "追踪名单为空，无需导出")
                    )
                    return@launch
                }

                val json = JSONObject().apply {
                    put("version", 1)
                    put("exportedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date()))
                    put("count", all.size)
                    put("creators", JSONArray().apply {
                        all.forEach { c ->
                            put(JSONObject().apply {
                                put("mid", c.mid.toString())
                                put("name", c.name)
                                put("face", c.face)
                            })
                        }
                    })
                }

                val jsonStr = json.toString(2)
                val file = withContext(Dispatchers.IO) {
                    val dir = context.cacheDir
                    val f = File(dir, "bilibili-tracking-list-${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())}.json")
                    f.writeText(jsonStr)
                    f
                }

                _uiState.value = _uiState.value.copy(exportFile = file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "导出失败", e)
                _uiState.value = _uiState.value.copy(
                    importResult = ImportDialogState(added = 0, skipped = 0, error = "导出失败：${e.message}")
                )
            }
        }
    }

    fun importTrackingList(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                        ?: throw Exception("无法读取文件")
                }

                val json = JSONObject(text)
                val arr = json.getJSONArray("creators")
                if (arr.length() == 0) {
                    _uiState.value = _uiState.value.copy(
                        importResult = ImportDialogState(added = 0, skipped = 0, error = "文件中没有有效的UP主数据")
                    )
                    return@launch
                }

                val creators = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    val midStr = obj.optString("mid", "")
                    val mid = midStr.toLongOrNull() ?: return@map null
                    CreatorEntity(
                        mid = mid,
                        name = obj.optString("name", "UP主_$mid"),
                        face = obj.optString("face", "")
                    )
                }.filterNotNull()

                if (creators.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        importResult = ImportDialogState(added = 0, skipped = 0, error = "文件中没有有效的UP主数据")
                    )
                    return@launch
                }

                val result = creatorRepo.importCreators(creators)
                _uiState.value = _uiState.value.copy(importResult = ImportDialogState(added = result.imported, skipped = result.skipped))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "导入失败", e)
                _uiState.value = _uiState.value.copy(
                    importResult = ImportDialogState(added = 0, skipped = 0, error = "导入失败：${e.message}")
                )
            }
        }
    }

    private fun parseMid(input: String): Long? {
        val trimmed = input.trim()
        val match = MID_REGEX.find(trimmed)
        if (match != null) return match.groupValues[1].toLongOrNull()
        return trimmed.toLongOrNull()
    }

    companion object {
        private val MID_REGEX = Regex("space\\.bilibili\\.com/(\\d+)")
    }
}
