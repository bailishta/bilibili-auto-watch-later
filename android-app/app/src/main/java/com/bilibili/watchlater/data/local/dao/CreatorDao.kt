package com.bilibili.watchlater.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.bilibili.watchlater.data.local.entity.CreatorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreatorDao {

    @Query("SELECT * FROM creators ORDER BY added_at DESC")
    fun getAll(): Flow<List<CreatorEntity>>

    @Query("SELECT * FROM creators ORDER BY added_at DESC")
    suspend fun getAllList(): List<CreatorEntity>

    @Query("SELECT * FROM creators WHERE mid = :mid")
    suspend fun getByMid(mid: Long): CreatorEntity?

    // 只更新排序字段；不会覆盖用户信息，也不会重新插入已被移除的UP主。
    @Query("""
        UPDATE creators SET
            fans = COALESCE(:fans, fans),
            last_pubdate = CASE WHEN :lastPubdate IS NULL THEN last_pubdate
                ELSE MAX(COALESCE(last_pubdate, 0), :lastPubdate) END,
            meta_updated_at = :updatedAt
        WHERE mid = :mid
    """)
    suspend fun updateMeta(mid: Long, fans: Long?, lastPubdate: Long?, updatedAt: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM creators WHERE mid = :mid)")
    suspend fun exists(mid: Long): Boolean

    @Upsert
    suspend fun insert(creator: CreatorEntity)

    @Query("DELETE FROM creators")
    suspend fun deleteAll()

    @Query("DELETE FROM creators WHERE mid = :mid")
    suspend fun deleteByMid(mid: Long)

    @Query("SELECT COUNT(*) FROM creators")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(creators: List<CreatorEntity>): List<Long>
}
