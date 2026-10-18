package com.bilibili.watchlater.data.repository

import com.bilibili.watchlater.data.local.dao.CreatorDao
import com.bilibili.watchlater.data.local.dao.TrackedVideoDao
import com.bilibili.watchlater.data.local.dao.WatchedVideoDao
import com.bilibili.watchlater.data.local.entity.CreatorEntity
import com.bilibili.watchlater.data.local.entity.TrackedVideoEntity
import com.bilibili.watchlater.data.local.entity.WatchedVideoEntity
import com.bilibili.watchlater.data.remote.CheckApi
import com.bilibili.watchlater.data.remote.BiliVideo
import com.bilibili.watchlater.util.AppLogger
import androidx.room.withTransaction
import com.bilibili.watchlater.data.local.AppDatabase
import com.bilibili.watchlater.data.local.entity.CheckSessionEntity
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

data class CheckReport(
    val newVideos: List<NewVideo> = emptyList(),
    val added: Int = 0,
    val errors: Int = 0,
    val status: String = "ok"
)

data class NewVideo(
    val bvid: String,
    val title: String,
    val author: String,
    val mid: Long,
    val pubdate: Long
)

class CheckRepository(
    private val api: CheckApi,
    private val creatorDao: CreatorDao,
    private val videoDao: TrackedVideoDao,
    private val settingsRepo: SettingsRepository,
    private val activityRepo: ActivityRepository,
    private val watchedVideoDao: WatchedVideoDao,
    private val database: AppDatabase,
    private val isOnline: () -> Boolean
) {
    companion object {
        private const val CONCURRENCY = 6
        private const val BATCH_STAGGER = 30L
        private const val REQUEST_DELAY = 200L
        private const val MAX_TRACKED_VIDEOS = 5000
        private val DELETED_NAMES = setOf("已注销用户", "账号已注销", "已注销")
    }

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    private val checkMutex = Mutex()

    suspend fun pendingSession(): CheckSession? = database.checkSessionDao().get()?.let { CheckSession.decode(it.payload) }

    private suspend fun save(session: CheckSession) {
        database.checkSessionDao().save(CheckSessionEntity(payload = session.encode()))
    }

    suspend fun discardPending() = checkMutex.withLock {
        pendingSession()?.let { finishCheck(it, "cancelled") }
    }

    suspend fun checkForNewVideos(
        expectedSessionId: String? = null,
        onSessionReady: (CheckSession) -> Unit = {},
        onProgress: (current: Int, total: Int, name: String) -> Unit
    ): CheckReport = checkMutex.withLock {
        cancelled = false
        var session = pendingSession()
        // A delayed system job must never create a new check or take over a newer one.
        if (expectedSessionId != null && session?.id != expectedSessionId) {
            return@withLock CheckReport(status = "stale_session")
        }
        val uid = api.getCurrentUserId()
        // Never resume a previous account's requests using the current account's cookies.
        if (session != null && session.uid != uid) {
            finishCheck(session, "cancelled")
            session = null
            if (expectedSessionId != null) return@withLock CheckReport(status = "stale_session")
        }
        var current = session ?: CheckSession(
            uid = uid,
            targets = creatorDao.getAllList().map { CheckTarget(it.mid, it.name) },
            windowHours = settingsRepo.newVideoWindowHours,
            cutoff = System.currentTimeMillis() / 1000 - settingsRepo.newVideoWindowHours * 3600L
        ).also { save(it) }
        if (current.targets.isEmpty()) return@withLock finishCheck(current, "empty_list")
        onSessionReady(current)
        onProgress(current.next, current.targets.size, "准备检查")
        AppLogger.i(TAG, "检查开始/恢复：${current.next}/${current.targets.size}，窗口${current.windowHours}h")
        try {
            if (!isOnline()) throw IOException("网络未连接")
            val windowMs = current.windowHours * 3600_000L
            val dayMs = 24 * 3600_000L
            val retentionDays = (2 * windowMs + dayMs - 1) / dayMs + 1
            val historyCutoff = LocalDate.now().minusDays(retentionDays - 1)
                .atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
            watchedVideoDao.deleteExpired(historyCutoff)
            val watchedBvids = watchedVideoDao.getBvids(uid).toMutableSet()
            if (!current.historyReady) {
                try {
                    if (settingsRepo.reserveHistorySync()) {
                        api.getWatchHistory(500) { page ->
                            val recent = page.filterValues { it >= historyCutoff }
                            watchedBvids.addAll(recent.keys)
                            watchedVideoDao.insertAll(recent.map { WatchedVideoEntity(uid, it.key, it.value) })
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: IOException) { throw e }
                catch (e: Exception) { AppLogger.w(TAG, "同步观看历史失败，继续使用本地记录", e) }
                current = current.copy(historyReady = true)
                save(current)
            }
            while (current.next < current.targets.size) {
                if (cancelled) return@withLock finishCheck(current, "cancelled")
                if (!isOnline()) throw IOException("网络未连接")
                val batch = current.targets.drop(current.next).take(CONCURRENCY)
                // Fetch concurrently, but persist completion only after every video for this UP is handled.
                val results = coroutineScope {
                    batch.mapIndexed { index, target -> async {
                        delay(index * BATCH_STAGGER)
                        try { Result.success(readUserVideos(target.mid)) }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { Result.failure(e) }
                    } }.awaitAll()
                }
                for ((index, target) in batch.withIndex()) {
                    if (cancelled) return@withLock finishCheck(current, "cancelled")
                    val result = results[index].getOrThrow()
                    if (!isOnline()) throw IOException("网络未连接")
                    onProgress(current.next, current.targets.size, target.name)
                    var errors = current.errors
                    if (result.error != null) {
                        errors++
                    } else {
                        updateCreatorMeta(target.mid, result.videos)
                        for (video in result.videos) {
                            if (cancelled) return@withLock finishCheck(current, "cancelled")
                            if (video.pubdate < current.cutoff || video.bvid in watchedBvids || videoDao.exists(video.bvid)) continue
                            if (!isOnline()) throw IOException("网络未连接")
                            val added = api.addToWatchLaterBatch(listOf(video.aid)).single()
                            if (added.success) {
                                val newVideo = NewVideo(video.bvid, video.title, video.author, video.mid, video.pubdate)
                                val updated = current.copy(videos = current.videos + newVideo)
                                // Video deduplication and checkpoint are committed atomically.
                                database.withTransaction {
                                    videoDao.insert(TrackedVideoEntity(video.bvid, video.title, video.mid, video.author, video.pubdate))
                                    save(updated)
                                }
                                current = updated
                            } else errors++
                            delay(REQUEST_DELAY)
                        }
                    }
                    current = current.copy(next = current.next + 1, errors = errors)
                    save(current)
                    onProgress(current.next, current.targets.size, target.name)
                }
                if (current.next < current.targets.size) delay(REQUEST_DELAY)
            }
            finishCheck(current, if (cancelled) "cancelled" else "ok")
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: IOException) {
            val status = if (isOnline()) "paused_connection" else "paused_network"
            AppLogger.w(TAG, "检查暂停：$status，已保存 ${current.next}/${current.targets.size}", e)
            if (cancelled) finishCheck(current, "cancelled")
            else CheckReport(current.videos, current.videos.size, current.errors, status)
        }
    }

    private suspend fun readUserVideos(mid: Long): com.bilibili.watchlater.data.remote.UserVideosResult {
        try { return api.getUserVideos(mid) }
        catch (error: IOException) {
            if (cancelled || !isOnline()) throw error
            // One retry for a read request; never loop indefinitely or replay a POST here.
            AppLogger.w(TAG, "UP主 $mid 请求失败，连接仍可用，750ms 后重试一次", error)
            delay(750)
            if (cancelled || !isOnline()) throw error
            return api.getUserVideos(mid)
        }
    }

    private suspend fun finishCheck(session: CheckSession, status: String): CheckReport {
        val count = videoDao.count()
        if (count > MAX_TRACKED_VIDEOS) videoDao.deleteOldest(count - MAX_TRACKED_VIDEOS)
        settingsRepo.finishCheck(session.id, session.videos.size)
        database.checkSessionDao().clear()
        AppLogger.i(TAG, "检查结束：$status，新增${session.videos.size}，错误${session.errors}")
        return CheckReport(session.videos, session.videos.size, session.errors, status)
    }

    // 粉丝数查询失败不影响新视频检查；缺失值保留数据库里的旧数据。
    private suspend fun updateCreatorMeta(mid: Long, videos: List<BiliVideo>) {
        val fans = try {
            api.getUpInfo(mid).fans
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "获取UP主${mid}粉丝数失败", e)
            null
        }
        if (fans == null && videos.isEmpty()) return
        try {
            activityRepo.recordMeta(mid, fans, videos)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "写回UP主${mid}排序数据失败", e)
        }
    }

    suspend fun importFollowList(uid: Long, onProgress: (page: Int, imported: Int, total: Int) -> Unit): ImportResult {
        var imported = 0
        var skipped = 0
        var total = 0
        var status = "ok"
        var page = 1
        var hasMore = true

        while (hasMore) {
            try {
                val r = api.getFollowList(uid, page)
                if (page == 1) total = r.total

                for (user in r.list) {
                    if (DELETED_NAMES.contains(user.name.trim())) {
                        skipped++
                        continue
                    }
                    if (creatorDao.exists(user.mid)) continue
                    creatorDao.insert(
                        CreatorEntity(
                            mid = user.mid,
                            name = user.name,
                            face = user.face
                        )
                    )
                    imported++
                }

                onProgress(page, imported, total)
                hasMore = r.hasMore
                page++
                delay(500)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "导入关注列表失败 page=$page", e)
                status = "error"
                break
            }
        }

        settingsRepo.importDone = true
        return ImportResult(imported, skipped, total, status)
    }


}

data class ImportResult(
    val imported: Int = 0,
    val skipped: Int = 0,
    val total: Int = 0,
    val status: String = "ok"
)

private const val TAG = "Checker"
