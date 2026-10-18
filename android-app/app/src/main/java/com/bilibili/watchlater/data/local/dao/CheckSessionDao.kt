package com.bilibili.watchlater.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bilibili.watchlater.data.local.entity.CheckSessionEntity

@Dao
interface CheckSessionDao {
    @Query("SELECT * FROM check_session WHERE id = 1")
    suspend fun get(): CheckSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(session: CheckSessionEntity)

    @Query("DELETE FROM check_session")
    suspend fun clear()
}
