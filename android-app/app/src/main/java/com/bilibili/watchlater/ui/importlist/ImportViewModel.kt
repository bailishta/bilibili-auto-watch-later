package com.bilibili.watchlater.ui.importlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.watchlater.data.repository.CheckRepository
import com.bilibili.watchlater.data.repository.CreatorRepository
import com.bilibili.watchlater.data.repository.ImportResult
import com.bilibili.watchlater.data.repository.SettingsRepository
import com.bilibili.watchlater.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ImportUiState(
    val isImporting: Boolean = false,
    val progressText: String = "",
    val result: ImportResult? = null,
    val error: String? = null
)

class ImportViewModel(
    private val checkRepo: CheckRepository,
    private val creatorRepo: CreatorRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun startImport(uid: Long) {
        viewModelScope.launch {
            _uiState.value = ImportUiState(isImporting = true, progressText = "正在获取关注列表...")

            try {
                val result = checkRepo.importFollowList(uid) { page, imported, total ->
                    _uiState.value = _uiState.value.copy(
                        progressText = "正在导入... 第${page}页，已导入 $imported / $total"
                    )
                }
                AppLogger.i(TAG, "导入完成：成功${result.imported}，跳过${result.skipped}，总计${result.total}")
                _uiState.value = ImportUiState(result = result)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "导入关注列表失败", e)
                _uiState.value = ImportUiState(error = "导入失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun skip() {
        settingsRepo.importDone = true
        AppLogger.i(TAG, "用户跳过导入关注列表")
    }
}

private const val TAG = "ImportVM"
