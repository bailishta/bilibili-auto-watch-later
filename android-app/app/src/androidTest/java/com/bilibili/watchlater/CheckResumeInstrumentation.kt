package com.bilibili.watchlater

import android.app.Activity
import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.app.ActivityManager
import android.app.job.JobScheduler
import android.content.Intent
import android.content.ContextWrapper
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.room.Room
import com.bilibili.watchlater.data.local.AppDatabase
import com.bilibili.watchlater.data.local.entity.CreatorEntity
import com.bilibili.watchlater.data.remote.*
import com.bilibili.watchlater.data.repository.*
import com.bilibili.watchlater.service.CheckRecovery
import com.bilibili.watchlater.service.CheckNotifications
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Self-contained device tests: fake network, real Room, isolated test-APK preferences. */
class CheckResumeInstrumentation : Instrumentation() {
    private val storageContext by lazy {
        object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("check_resume_test_$name", mode)
        }
    }
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val output = StringBuilder()
        try {
            runBlocking {
                fixture(3) { f ->
                    f.api.failOnceMid = 2
                    val report = f.repo.checkForNewVideos { _, _, _ -> }
                    check(report.status == "ok" && report.added == 3)
                    check(f.api.queries.count { it == 2L } == 2)
                    check(f.repo.pendingSession() == null)
                }
                output.appendLine("PASS: one transient timeout retries once and finishes without pausing")
                fixture(8) { f ->
                    f.api.failMid = 3
                    val paused = f.repo.checkForNewVideos { _, _, _ -> }
                    check(paused.status == "paused_connection")
                    check(f.api.queries.count { it == 3L } == 2)
                    val saved = checkNotNull(f.repo.pendingSession())
                    check(saved.next == 2 && saved.videos.size == 2)
                    check(f.settings.lastCheck == 0L)
                    f.reopen()
                    check(f.repo.pendingSession() == saved)
                    f.settings.newVideoWindowHours = 168
                    f.api.includeOldVideo = true
                    f.db.creatorDao().insert(CreatorEntity(99, "new after pause", addedAt = 2000))
                    f.api.failMid = null
                    f.api.queries.clear()
                    val progress = mutableListOf<Int>()
                    val done = f.repo.checkForNewVideos { current, _, _ -> progress += current }
                    check(done.status == "ok" && done.added == 8)
                    check(f.api.queries.none { it == 1L || it == 2L || it == 99L })
                    check(progress.first() == 2 && progress.last() == 8 && progress.zipWithNext().all { it.first <= it.second })
                    check(f.settings.totalAdded == 8 && f.settings.lastNewCount == 8)
                    check(f.repo.pendingSession() == null)
                    f.settings.finishCheck(saved.id, 8)
                    check(f.settings.totalAdded == 8)
                }
                output.appendLine("PASS: offline pause, database reopen, frozen list/window, resume cursor, progress and idempotent totals")
                fixture(1) { f ->
                    f.api.videosPerCreator = 3
                    f.api.failAid = 12
                    check(f.repo.checkForNewVideos { _, _, _ -> }.status == "paused_connection")
                    check(f.repo.pendingSession()?.next == 0)
                    check(f.repo.pendingSession()?.videos?.size == 1)
                    f.reopen()
                    f.api.failAid = null
                    check(f.repo.checkForNewVideos { _, _, _ -> }.added == 3)
                    check(f.api.added == listOf(11L, 12L, 13L))
                    check(f.settings.totalAdded == 3)
                }
                output.appendLine("PASS: interruption midway through adding one creator's videos, no duplicate additions")
                fixture(4) { f ->
                    val cancelled = f.repo.checkForNewVideos { current, _, _ -> if (current == 1) f.repo.cancel() }
                    check(cancelled.status == "cancelled" && cancelled.added == 1)
                    check(f.repo.pendingSession() == null && f.settings.totalAdded == 1)
                }
                output.appendLine("PASS: explicit cancellation removes resume checkpoint")
                fixture(2) { f ->
                    f.online = false
                    check(f.repo.checkForNewVideos { _, _, _ -> }.status == "paused_network")
                    check(f.api.queries.isEmpty() && f.repo.pendingSession()?.next == 0)
                    f.repo.discardPending()
                    check(f.repo.pendingSession() == null)
                }
                output.appendLine("PASS: offline start sends no requests; paused check can be discarded")
                fixture(2) { f ->
                    check(f.repo.checkForNewVideos(expectedSessionId = "missing") { _, _, _ -> }.status == "stale_session")
                    check(f.api.queries.isEmpty() && f.repo.pendingSession() == null)
                    f.online = false
                    f.repo.checkForNewVideos { _, _, _ -> }
                    val saved = checkNotNull(f.repo.pendingSession())
                    f.online = true
                    check(f.repo.checkForNewVideos(expectedSessionId = "old-session") { _, _, _ -> }.status == "stale_session")
                    check(f.repo.pendingSession() == saved && f.api.queries.isEmpty())
                    check(f.repo.checkForNewVideos(expectedSessionId = saved.id) { _, _, _ -> }.added == 2)
                    check(f.repo.checkForNewVideos(expectedSessionId = saved.id) { _, _, _ -> }.status == "stale_session")
                    check(f.api.added.size == 2)
                }
                output.appendLine("PASS: delayed recovery never starts a new check or repeats a completed session")
                fixture(2) { f ->
                    f.api.businessErrorMid = 1
                    val report = f.repo.checkForNewVideos { _, _, _ -> }
                    check(report.status == "ok" && report.errors == 1 && report.added == 1)
                    check(f.repo.pendingSession() == null)
                }
                output.appendLine("PASS: API business errors are distinct from transport interruption")
                fixture(4) { f ->
                    val app = targetContext.applicationContext as BiliApp
                    val field = BiliApp::class.java.getDeclaredField("checkRepo").apply { isAccessible = true }
                    val original = field.get(app)
                    field.set(app, f.repo)
                    val activity = startActivitySync(Intent().setClassName(targetContext.packageName,
                        "com.bilibili.watchlater.CheckTestActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    try {
                        uiAutomation.executeShellCommand("pm grant ${targetContext.packageName} android.permission.POST_NOTIFICATIONS").close()
                        f.api.blockAid = 21
                        runOnMainSync { app.checkController.start() }
                        val notifications = targetContext.getSystemService(NotificationManager::class.java)
                        val scheduler = targetContext.getSystemService(JobScheduler::class.java)
                        notifications.cancel(CheckNotifications.RESULT_ID)
                        val services = targetContext.getSystemService(ActivityManager::class.java)
                        withTimeout(15_000) {
                            while (notifications.activeNotifications.none {
                                it.notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX) == 4 &&
                                    it.notification.extras.getInt(Notification.EXTRA_PROGRESS) == 1
                            }) delay(100)
                        }
                        check(services.getRunningServices(100).any { it.service.className.endsWith("CheckService") })
                        check(hasCheckWakeLock())
                        // Simulate the reported case: screen stays on while another activity is foreground.
                        targetContext.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        delay(1_000)
                        check(app.checkController.state.value.running && hasCheckWakeLock())
                        f.api.failAid = 21
                        f.api.gate.complete(Unit)
                        withTimeout(15_000) {
                            while (!app.checkController.state.value.paused ||
                                notifications.activeNotifications.isNotEmpty() ||
                                services.getRunningServices(100).any { it.service.className.endsWith("CheckService") }) delay(100)
                        }
                        check(f.repo.pendingSession()?.next == 1)
                        check(!hasCheckWakeLock())
                        check(scheduler.getPendingJob(CheckRecovery.JOB_ID) != null)
                        // Recover while Settings is still in front, without reopening our Activity.
                        f.api.failAid = null
                        ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(
                            "cmd jobscheduler run -f ${targetContext.packageName} ${CheckRecovery.JOB_ID}"
                        )).bufferedReader().use { it.readText() }
                        withTimeout(15_000) {
                            while (app.checkController.state.value.report?.status != "ok" ||
                                notifications.activeNotifications.none { it.id == CheckNotifications.RESULT_ID } ||
                                services.getRunningServices(100).any { it.service.className.endsWith("CheckService") }) delay(100)
                        }
                        check(app.checkController.state.value.report?.added == 4)
                        check(f.repo.pendingSession() == null)
                        check(!hasCheckWakeLock())
                        val result = notifications.activeNotifications.single { it.id == CheckNotifications.RESULT_ID }.notification
                        check(result.channelId == CheckNotifications.RESULT_CHANNEL)
                        check(result.extras.getString(Notification.EXTRA_TEXT)?.contains("4") == true)
                        check(result.flags and Notification.FLAG_AUTO_CANCEL != 0)
                        check(result.flags and Notification.FLAG_ONGOING_EVENT == 0)
                        withTimeout(5_000) { while (scheduler.getPendingJob(CheckRecovery.JOB_ID) != null) delay(100) }
                        notifications.cancel(CheckNotifications.RESULT_ID)
                        runOnMainSync {
                            activity.startActivity(Intent().setClassName(targetContext.packageName,
                                "com.bilibili.watchlater.CheckTestActivity").addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                        }
                        delay(500)
                        f.db.creatorDao().insert(CreatorEntity(99, "cancel test", addedAt = 2000))
                        f.api.blockAid = 991
                        f.api.gate = CompletableDeferred()
                        runOnMainSync { app.checkController.start() }
                        withTimeout(15_000) { while (99L !in f.api.queries) delay(100) }
                        withContext(Dispatchers.Main) { app.checkController.cancel() }
                        withTimeout(5_000) {
                            while (app.checkController.state.value.running ||
                                notifications.activeNotifications.isNotEmpty() ||
                                services.getRunningServices(100).any { it.service.className.endsWith("CheckService") }) delay(100)
                        }
                        check(f.repo.pendingSession() == null)
                        check(scheduler.getPendingJob(CheckRecovery.JOB_ID) == null)
                        check(!hasCheckWakeLock())
                        CheckNotifications.completed(targetContext, CheckReport(added = 0))
                        withTimeout(5_000) { while (notifications.activeNotifications.none { it.id == CheckNotifications.RESULT_ID }) delay(100) }
                        check(notifications.activeNotifications.single { it.id == CheckNotifications.RESULT_ID }
                            .notification.extras.getString(Notification.EXTRA_TEXT) == "本次没有新增视频")
                        notifications.cancel(CheckNotifications.RESULT_ID)
                        CheckNotifications.completed(targetContext, CheckReport(status = "paused_network"))
                        CheckNotifications.completed(targetContext, CheckReport(status = "cancelled"))
                        check(notifications.activeNotifications.none { it.id == CheckNotifications.RESULT_ID })
                    } finally {
                        CheckRecovery.cancel(targetContext)
                        targetContext.getSystemService(NotificationManager::class.java).cancel(CheckNotifications.RESULT_ID)
                        runOnMainSync { activity.finish() }
                        field.set(app, original)
                    }
                }
                output.appendLine("PASS: system job resumes in background; completion and zero-result notifications persist; cancellation removes recovery without a completion alert")
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", output.toString()) })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", output.toString() + error.stackTraceToString()) })
        }
    }

    private suspend fun fixture(count: Int, block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try {
            for (mid in 1..count) f.db.creatorDao().insert(CreatorEntity(mid.toLong(), "UP $mid", addedAt = 1000L - mid))
            block(f)
        } finally { f.db.close(); storageContext.deleteDatabase("check-resume-test.db") }
    }

    private fun hasCheckWakeLock(): Boolean {
        val dump = ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("dumpsys power"))
            .bufferedReader().use { it.readText() }
        return dump.lineSequence().any { "PARTIAL_WAKE_LOCK" in it && "BiliWatchLater:Check" in it }
    }

    private inner class Fixture {
        init { storageContext.getSharedPreferences("settings", 0).edit().clear().commit() }
        var db = openDb()
        val settings = SettingsRepository(storageContext)
        val api = FakeApi()
        var online = true
        var repo = repository()
        private fun openDb() = Room.databaseBuilder(storageContext, AppDatabase::class.java, "check-resume-test.db").build()
        private fun repository() = CheckRepository(api, db.creatorDao(), db.trackedVideoDao(), settings,
            ActivityRepository(db, settings), db.watchedVideoDao(), db, { online })
        fun reopen() { db.close(); db = openDb(); repo = repository() }
    }

    private class FakeApi : CheckApi {
        var failMid: Long? = null
        var failOnceMid: Long? = null
        var failAid: Long? = null
        var businessErrorMid: Long? = null
        var videosPerCreator = 1
        var includeOldVideo = false
        var blockAid: Long? = null
        var gate = CompletableDeferred<Unit>()
        val queries = mutableListOf<Long>()
        val added = mutableListOf<Long>()
        override suspend fun getCurrentUserId() = "fixture-user"
        override suspend fun getUpInfo(mid: Long) = UpInfo(mid, "UP $mid", "", 100)
        override suspend fun getUserVideos(mid: Long, page: Int, ps: Int): UserVideosResult {
            queries += mid
            if (mid == failOnceMid) {
                failOnceMid = null
                throw java.net.SocketTimeoutException("simulated transient timeout")
            }
            if (mid == failMid) throw IOException("simulated disconnect")
            if (mid == businessErrorMid) return UserVideosResult("API rejected", emptyList())
            val videos = (1..videosPerCreator).map { index ->
                BiliVideo(mid * 10 + index, "BV${mid}_$index", "video $index", System.currentTimeMillis() / 1000, "UP $mid", mid, "")
            }.toMutableList()
            if (includeOldVideo && mid == 3L) videos += BiliVideo(39, "BVold", "old", System.currentTimeMillis() / 1000 - 48 * 3600, "UP 3", mid, "")
            return UserVideosResult(null, videos)
        }
        override suspend fun addToWatchLaterBatch(aids: List<Long>): List<BatchResult> = aids.map {
            if (it == blockAid) gate.await()
            if (it == failAid) throw IOException("simulated disconnect during add")
            check(it !in added) { "duplicate add: $it" }
            added += it
            BatchResult(it, true, 0)
        }
        override suspend fun getWatchHistory(targetCount: Int, onPage: suspend (Map<String, Long>) -> Unit) = emptySet<String>()
        override suspend fun getFollowList(uid: Long, page: Int, ps: Int) = FollowListResult(emptyList(), 0, false)
    }
}
