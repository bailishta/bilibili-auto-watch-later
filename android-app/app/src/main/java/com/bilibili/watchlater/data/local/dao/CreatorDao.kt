package com.bilibili.watchlater.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bilibili.watchlater.data.local.entity.CreatorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreatorDao {

    @Query("SELECT * FROM creators ORDER BY added_at DESC")
    fun getAll(): Flow<List<CreatorEntity>>

    @Query("SELECT * FROM creators ORDER BY added_at DESC")
    suspend fun getAllList(): List<CreatorEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM creators WHERE mid = :mid)")
    suspend fun exists(mid: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(creator: CreatorEntity)

    @Query("DELETE FROM creators WHERE mid = :mid")
    suspend fun deleteByMid(mid: Long)

    @Query("SELECT COUNT(*) FROM creators")
    suspend fun count(): Int
}
