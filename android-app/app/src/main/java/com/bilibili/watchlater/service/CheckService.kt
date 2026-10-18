package com.bilibili.watchlater.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.bilibili.watchlater.BiliApp
import com.bilibili.watchlater.MainActivity
import com.bilibili.watchlater.util.AppLogger
import com.bilibili.watchlater.util.checkEnvironment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class CheckService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val controller get() = (application as BiliApp).checkController

    override fun onCreate() {
        super.onCreate()
        AppLogger.i("CheckService", "服务创建；${checkEnvironment()}")
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "检查进度", NotificationManager.IMPORTANCE_LOW).apply {
                description = "仅在检查 UP 主更新时显示进度"
                setSound(null, null)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (job?.isActive == true) return START_NOT_STICKY
        try {
            ServiceCompat.startForeground(this, NOTIFICATION, notification(controller.state.value),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        } catch (error: Exception) {
            AppLogger.e("CheckService", "启动前台检查失败", error)
            controller.interrupted()
            stopSelf()
            return START_NOT_STICKY
        }
        AppLogger.i("CheckService", "前台检查启动；${checkEnvironment()}")
        job = scope.launch {
            val updates = launch {
                controller.state.collectLatest { state ->
                    if (state.running) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(state))
                }
            }
            try {
                // Held only during this check; no alarms, restart loops or permanent keep-alive.
                wakeLock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BiliWatchLater:Check").apply {
                        setReferenceCounted(false)
                        acquire(MAX_RUN_MS)
                    }
                withTimeout(MAX_RUN_MS) { controller.run() }
            } catch (error: TimeoutCancellationException) {
                AppLogger.w("CheckService", "单次检查达到30分钟上限，保留进度并停止")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("CheckService", "检查服务异常", error)
                controller.interrupted()
            }
            finally {
                updates.cancel()
                releaseWakeLock()
                AppLogger.i("CheckService", "检查服务停止，通知与运行保护已释放")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(state: CheckState) = CheckNotifications.builder(this, CHANNEL)
        .setContentTitle("正在检查 UP 主更新")
        .setContentText(state.text)
        .setProgress(state.total.coerceAtLeast(1), state.current, state.total == 0)
        .setOnlyAlertOnce(true).setOngoing(true).setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 dataSync time limit: keep the durable checkpoint and stop promptly.
        job?.cancel()
        AppLogger.w("CheckService", "系统触发前台服务时限 type=$fgsType")
        releaseWakeLock()
        controller.interrupted()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        AppLogger.i("CheckService", "服务销毁，任务活动=${job?.isActive}")
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        AppLogger.i("CheckService", "应用从最近任务移除，任务活动=${job?.isActive}")
        super.onTaskRemoved(rootIntent)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL = "check_progress"
        private const val NOTIFICATION = CheckNotifications.PROGRESS_ID
        private const val MAX_RUN_MS = 30 * 60 * 1000L
    }
}
