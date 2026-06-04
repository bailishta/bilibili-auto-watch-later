package com.bilibili.watchlater.data.repository

import com.bilibili.watchlater.data.local.dao.CreatorDao
import com.bilibili.watchlater.data.local.dao.TrackedVideoDao
import com.bilibili.watchlater.data.local.entity.CreatorEntity
import com.bilibili.watchlater.data.local.entity.TrackedVideoEntity
import com.bilibili.watchlater.data.remote.BiliApiService
import com.bilibili.watchlater.util.AppLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

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
    private val api: BiliApiService,
    private val creatorDao: CreatorDao,
    private val videoDao: TrackedVideoDao,
    private val settingsRepo: SettingsRepository
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

    suspend fun checkForNewVideos(
        onProgress: (current: Int, total: Int, name: String) -> Unit
    ): CheckReport = coroutineScope {
        cancelled = false

        val creators = creatorDao.getAllList()
        if (creators.isEmpty()) {
            AppLogger.i(TAG, "检查开始：追踪名单为空，跳过")
            return@coroutineScope CheckReport(status = "empty_list")
        }
        AppLogger.i(TAG, "检查开始：${creators.size}位UP主，窗口${settingsRepo.newVideoWindowHours}h")

        val windowMs = settingsRepo.newVideoWindowHours * 60 * 60 * 1000L
        val cutOff = (System.currentTimeMillis() - windowMs) / 1000

        var watchedBvids = emptySet<String>()
        try {
            watchedBvids = api.getWatchHistory(500)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "获取观看历史失败，跳过去重", e)
        }

        val newVideoList = mutableListOf<NewVideo>()
        var addedCount = 0
        var errorCount = 0
        var checked = 0

        for (i in creators.indices step CONCURRENCY) {
            if (cancelled) {
                return@coroutineScope CheckReport(
                    newVideos = newVideoList,
                    added = addedCount,
                    errors = errorCount,
                    status = "cancelled"
                )
            }

            val batch = creators.subList(i, minOf(i + CONCURRENCY, creators.size))

            val batchResults = batch.mapIndexed { bi, creator ->
                async {
                    delay(bi * BATCH_STAGGER)
                    checked++
                    onProgress(checked, creators.size, creator.name)

                    try {
                        val result = api.getUserVideos(creator.mid)
                        if (result.error != null) {
                            return@async BatchCheckResult(creator.mid, creator.name, emptyList(), emptyList(), result.error)
                        }
                        // 先按时间和观看历史过滤
                        val candidates = result.videos.filter { v ->
                            v.pubdate >= cutOff && v.bvid !in watchedBvids
                        }
                        // 批量检查已追踪视频
                        val existingBvids = if (candidates.isNotEmpty()) {
                            videoDao.filterExisting(candidates.map { it.bvid }).toSet()
                        } else emptySet()
                        val newVids = mutableListOf<NewVideo>()
                        val aids = mutableListOf<Long>()
                        for (v in candidates) {
                            if (v.bvid in existingBvids) continue
                            aids.add(v.aid)
                            newVids.add(NewVideo(v.bvid, v.title, v.author, v.mid, v.pubdate))
                        }
                        if (result.videos.isNotEmpty() && newVids.isEmpty()) {
                            val firstPubdate = result.videos.first().pubdate
                            AppLogger.i(TAG, "UP主${creator.name}(mid=${creator.mid}) 共${result.videos.size}视频 首vid pubdate=$firstPubdate cutOff=$cutOff -> 候选${candidates.size} 新增0")
                        }
                        BatchCheckResult(creator.mid, creator.name, aids, newVids, null)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        BatchCheckResult(creator.mid, creator.name, emptyList(), emptyList(), e.message ?: e.javaClass.simpleName)
                    }
                }
            }.awaitAll()

            val allAids = mutableListOf<Long>()
            val allNewVids = mutableListOf<NewVideo>()
            for (r in batchResults) {
                if (r.error != null) {
                    errorCount++
                    continue
                }
                allAids.addAll(r.aidsToAdd)
                allNewVids.addAll(r.newVids)
            }

            if (allAids.isNotEmpty()) {
                val results = api.addToWatchLaterBatch(allAids)
                for (j in allAids.indices) {
                    val r = results.getOrNull(j) ?: continue
                    if (r.code == 0) {
                        val v = allNewVids[j]
                        videoDao.insert(
                            TrackedVideoEntity(
                                bvid = v.bvid,
                                title = v.title,
                                mid = v.mid,
                                author = v.author,
                                pubdate = v.pubdate
                            )
                        )
                        newVideoList += v
                        addedCount++
                    }
                }
            }

            if (i + CONCURRENCY < creators.size) {
                delay(REQUEST_DELAY)
            }
        }

        finishCheck(addedCount, newVideoList, errorCount)
    }

    private suspend fun finishCheck(addedCount: Int, newVideoList: List<NewVideo>, errorCount: Int): CheckReport {
        val count = videoDao.count()
        if (count > MAX_TRACKED_VIDEOS) {
            videoDao.deleteOldest(count - MAX_TRACKED_VIDEOS)
        }

        settingsRepo.totalAdded += addedCount
        settingsRepo.lastCheck = System.currentTimeMillis()
        settingsRepo.lastNewCount = addedCount

        AppLogger.i(TAG, "检查完成：新增$addedCount，错误${errorCount}，总计${settingsRepo.totalAdded}")

        return CheckReport(
            newVideos = newVideoList,
            added = addedCount,
            errors = errorCount,
            status = "ok"
        )
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

    private data class BatchCheckResult(
        val mid: Long,
        val name: String,
        val aidsToAdd: List<Long>,
        val newVids: List<NewVideo>,
        val error: String?
    )
}

data class ImportResult(
    val imported: Int = 0,
    val skipped: Int = 0,
    val total: Int = 0,
    val status: String = "ok"
)

private const val TAG = "Checker"
