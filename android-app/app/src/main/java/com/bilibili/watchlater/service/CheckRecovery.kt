package com.bilibili.watchlater.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import android.os.SystemClock
import com.bilibili.watchlater.BiliApp
import com.bilibili.watchlater.util.AppLogger
import kotlinx.coroutines.*

/** One network-constrained recovery job for the user's current check, never a periodic poll. */
object CheckRecovery {
    const val JOB_ID = 2001
    const val SESSION = "session"
    const val EXPIRES = "expires"
    private const val RECOVERY_WINDOW_MS = 60 * 60 * 1000L

    fun isScheduled(context: Context, sessionId: String): Boolean {
        val job = context.getSystemService(JobScheduler::class.java).getPendingJob(JOB_ID)
        return job?.extras?.getString(SESSION) == sessionId &&
            job.extras.getLong(EXPIRES) > SystemClock.elapsedRealtime()
    }

    fun schedule(context: Context, sessionId: String) {
        try {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (isScheduled(context, sessionId)) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, CheckRecoveryService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(30_000)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setExtras(PersistableBundle().apply {
                    putString(SESSION, sessionId)
                    putLong(EXPIRES, SystemClock.elapsedRealtime() + RECOVERY_WINDOW_MS)
                }).build()
            // No reboot receiver, exact alarms or battery exemption permission is needed.
            if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
                AppLogger.w("CheckRecovery", "系统未接受续查任务，仍可回到 App 继续")
            }
        } catch (error: Exception) {
            AppLogger.w("CheckRecovery", "无法安排系统续查，保留断点", error)
        }
    }

    fun cancel(context: Context, sessionId: String? = null) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (sessionId == null || scheduler.getPendingJob(JOB_ID)?.extras?.getString(SESSION) == sessionId) {
            scheduler.cancel(JOB_ID)
        }
    }
}

/** Runs inside the OS's allowed network window; does not start a foreground service from background. */
class CheckRecoveryService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var work: Job? = null
    private val app get() = application as BiliApp

    override fun onStartJob(params: JobParameters): Boolean {
        val sessionId = params.extras.getString(CheckRecovery.SESSION) ?: return false
        if (SystemClock.elapsedRealtime() >= params.extras.getLong(CheckRecovery.EXPIRES)) {
            AppLogger.i("CheckRecovery", "自动续查窗口已结束，保留断点供手动继续")
            return false
        }
        work = scope.launch {
            var retry = false
            try {
                if (app.checkController.state.value.running || app.checkController.state.value.cancelling) {
                    retry = true
                } else {
                    AppLogger.i("CheckRecovery", "系统允许联网，尝试从断点续查")
                    // Yield before Android's per-job time limit, retaining durable progress.
                    withTimeout(5 * 60 * 1000L) {
                        app.checkController.run(expectedSessionId = sessionId)
                    }
                    retry = app.checkRepo.pendingSession()?.id == sessionId
                }
            } catch (_: TimeoutCancellationException) {
                retry = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("CheckRecovery", "续查暂未完成，系统退避后再试", error)
                retry = true
            } finally {
                // onStopJob owns rescheduling after OS cancellation; never finish that execution twice.
                if (currentCoroutineContext().isActive) {
                    jobFinished(params, retry && SystemClock.elapsedRealtime() < params.extras.getLong(CheckRecovery.EXPIRES))
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        AppLogger.i("CheckRecovery", "系统收回运行窗口，保留断点")
        return SystemClock.elapsedRealtime() < params.extras.getLong(CheckRecovery.EXPIRES)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
