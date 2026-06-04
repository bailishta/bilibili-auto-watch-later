package com.bilibili.watchlater.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.watchlater.data.remote.BiliApiService
import com.bilibili.watchlater.data.repository.CheckReport
import com.bilibili.watchlater.data.repository.CheckRepository
import com.bilibili.watchlater.data.repository.CreatorRepository
import com.bilibili.watchlater.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val searchQuery: String = ""
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
