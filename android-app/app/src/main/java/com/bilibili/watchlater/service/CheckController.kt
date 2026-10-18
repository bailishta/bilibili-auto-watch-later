package com.bilibili.watchlater.service

import android.content.Intent
import androidx.core.content.ContextCompat
import com.bilibili.watchlater.BiliApp
import com.bilibili.watchlater.data.repository.CheckReport
import com.bilibili.watchlater.util.AppLogger
import com.bilibili.watchlater.util.checkEnvironment
import com.bilibili.watchlater.util.hasNetworkConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CheckState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val cancelling: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val text: String = "",
    val report: CheckReport? = null
)

/** Shared by the foreground service, OS recovery job and UI; one execution at a time. */
class CheckController(private val app: BiliApp) {
    private var runningJob: Job? = null
    private val mutableState = MutableStateFlow(CheckState())
    val state = mutableState.asStateFlow()

    suspend fun refreshPending() {
        if (state.value.running || state.value.cancelling) return
        val pending = app.checkRepo.pendingSession() ?: return
        val text = if (CheckRecovery.isScheduled(app, pending.id)) "进度已保存，等待系统续查；也可立即继续"
            else "进度已保存，回到 App 后可继续检查"
        if (!state.value.running && !state.value.cancelling) mutableState.value = CheckState(paused = true, current = pending.next,
            total = pending.targets.size, text = text)
    }

    fun start() {
        if (state.value.running || state.value.cancelling) return
        mutableState.update { it.copy(running = true, paused = false, cancelling = false, report = null, text = "准备检查…") }
        try {
            ContextCompat.startForegroundService(app, Intent(app, CheckService::class.java))
        } catch (error: Exception) {
            mutableState.update { it.copy(running = false, paused = it.total > 0, text = "无法开始检查：${error.message}") }
        }
    }

    suspend fun resumePending(canResume: () -> Boolean) {
        refreshPending()
        val pending = app.checkRepo.pendingSession()
        if (state.value.running || state.value.cancelling || pending == null) return
        if (app.cookieProvider.isLoggedIn &&
            pending.uid != app.cookieProvider.dedeUserId) {
            CheckRecovery.cancel(app, pending.id)
            app.checkRepo.discardPending()
            mutableState.value = CheckState(text = "账号已变更，已结束上次检查")
            return
        }
        if (state.value.paused && !state.value.running && app.cookieProvider.isLoggedIn &&
            app.hasNetworkConnection() && canResume()) start()
    }

    suspend fun cancel() {
        // Cancel the scheduled fallback as well as the current execution.
        if (state.value.running || state.value.paused) mutableState.update { it.copy(cancelling = true) }
        CheckRecovery.cancel(app)
        if (state.value.running) {
            mutableState.update { it.copy(cancelling = true) }
            app.checkRepo.cancel()
            runningJob?.cancel()
        } else if (state.value.paused) {
            mutableState.update { it.copy(cancelling = true) }
            try {
                app.checkRepo.discardPending()
                mutableState.value = CheckState(text = "检查已取消")
            } finally { mutableState.update { it.copy(cancelling = false) } }
        }
    }

    suspend fun run(expectedSessionId: String? = null) {
        if (runningJob != null) return
        runningJob = currentCoroutineContext().job
        var sessionId: String? = expectedSessionId
        try {
            if (state.value.cancelling) {
                app.checkRepo.discardPending()
                mutableState.value = CheckState(text = "检查已取消")
                return
            }
            mutableState.update { it.copy(running = true, paused = false, report = null, text = "准备检查…") }
            val report = app.checkRepo.checkForNewVideos(expectedSessionId, onSessionReady = { session ->
                sessionId = session.id
                if (expectedSessionId == null) CheckRecovery.schedule(app, session.id)
            }) { current, total, name ->
                mutableState.update { it.copy(current = current, total = total, text = "$current/$total · $name") }
            }
            if (report.status == "stale_session") {
                mutableState.value = CheckState()
                refreshPending()
                return
            }
            val paused = report.status == "paused_network" || report.status == "paused_connection"
            val recoveryText = if (sessionId?.let { CheckRecovery.isScheduled(app, it) } == true)
                "等待系统择机续查，也可回到 App 继续" else "请回到 App 继续检查"
            val text = when (report.status) {
                "paused_network" -> "网络未连接，进度已保存；$recoveryText"
                "paused_connection" -> "连接暂时失败，进度已保存；$recoveryText"
                "empty_list" -> "追踪名单为空，请先添加或导入 UP 主"
                "cancelled" -> "检查已取消，已添加 ${report.added} 个视频"
                else -> "检查完成：添加 ${report.added} 个视频" + if (report.errors > 0) "，${report.errors} 项失败" else ""
            }
            if (paused) AppLogger.w("CheckService", "暂停状态：${report.status}；${app.checkEnvironment()}")
            // A running JobService completes its own job; cancelling it here would interrupt this coroutine.
            if (!paused && expectedSessionId == null) sessionId?.let { CheckRecovery.cancel(app, it) }
            CheckNotifications.completed(app, report)
            mutableState.update { it.copy(running = false, paused = paused, cancelling = false, text = text, report = report) }
        } catch (error: CancellationException) {
            if (state.value.cancelling) withContext(NonCancellable) {
                try {
                    app.checkRepo.discardPending()
                    mutableState.value = CheckState(text = "检查已取消")
                } catch (failure: Exception) {
                    mutableState.update { it.copy(running = false, paused = true, cancelling = false, text = "取消失败：${failure.message}") }
                }
            } else interrupted()
            throw error
        } catch (error: Exception) {
            val pending = try { app.checkRepo.pendingSession() } catch (_: Exception) { null }
            mutableState.update { it.copy(running = false, paused = pending != null, cancelling = false,
                current = pending?.next ?: it.current, total = pending?.targets?.size ?: it.total,
                text = "检查暂停：${error.message ?: "请稍后重试"}") }
        } finally { runningJob = null }
    }

    fun interrupted() {
        AppLogger.w("CheckService", "检查执行被中断；${app.checkEnvironment()}")
        mutableState.update { it.copy(running = false, paused = true, cancelling = false, text = "检查已暂停，进度已保存；等待系统续查或回到 App 继续") }
    }
}
