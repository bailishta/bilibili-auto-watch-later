package com.bilibili.watchlater.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bilibili.watchlater.data.local.entity.TrackedVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedVideoDao {

    @Query("SELECT * FROM tracked_videos ORDER BY added_at DESC")
    fun getAll(): Flow<List<TrackedVideoEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM tracked_videos WHERE bvid = :bvid)")
    suspend fun exists(bvid: String): Boolean

    @Query("SELECT bvid FROM tracked_videos WHERE bvid IN (:bvids)")
    suspend fun filterExisting(bvids: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: TrackedVideoEntity)

    @Query("SELECT COUNT(*) FROM tracked_videos")
    suspend fun count(): Int

    @Query("DELETE FROM tracked_videos WHERE bvid IN (SELECT bvid FROM tracked_videos ORDER BY added_at ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}
