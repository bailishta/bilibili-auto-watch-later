package com.bilibili.watchlater.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.bilibili.watchlater.data.local.entity.WatchedVideoEntity

@Dao
interface WatchedVideoDao {
    @Query("SELECT bvid FROM watched_videos WHERE uid = :uid")
    suspend fun getBvids(uid: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(videos: List<WatchedVideoEntity>)

    @Query("UPDATE watched_videos SET view_at = MAX(view_at, :viewAt) WHERE uid = :uid AND bvid = :bvid")
    suspend fun updateViewAt(uid: String, bvid: String, viewAt: Long)

    @Transaction
    suspend fun insertAll(videos: List<WatchedVideoEntity>) {
        insertNew(videos)
        for (video in videos) updateViewAt(video.uid, video.bvid, video.viewAt)
    }

    @Query("DELETE FROM watched_videos WHERE view_at < :cutoff")
    suspend fun deleteExpired(cutoff: Long)
}
