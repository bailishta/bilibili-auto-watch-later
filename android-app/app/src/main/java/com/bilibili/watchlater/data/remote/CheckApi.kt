package com.bilibili.watchlater.data.remote

interface CheckApi {
    suspend fun getCurrentUserId(): String
    suspend fun getUpInfo(mid: Long): UpInfo
    suspend fun getUserVideos(mid: Long, page: Int = 1, ps: Int = 10): UserVideosResult
    suspend fun addToWatchLaterBatch(aids: List<Long>): List<BatchResult>
    suspend fun getWatchHistory(targetCount: Int = 500, onPage: suspend (Map<String, Long>) -> Unit = {}): Set<String>
    suspend fun getFollowList(uid: Long, page: Int = 1, ps: Int = 50): FollowListResult
}
