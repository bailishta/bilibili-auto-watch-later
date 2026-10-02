package com.bilibili.watchlater.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bilibili.watchlater.data.local.entity.PublicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PublicationDao {
    @Query("SELECT * FROM publications ORDER BY pubdate DESC")
    fun observeAll(): Flow<List<PublicationEntity>>

    @Query("SELECT * FROM publications ORDER BY pubdate DESC")
    suspend fun getAll(): List<PublicationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<PublicationEntity>)

    @Query("DELETE FROM publications WHERE pubdate < :cutoff OR pubdate > :now")
    suspend fun prune(cutoff: Long, now: Long)

    @Query("""DELETE FROM publications WHERE mid = :mid AND record_key NOT IN
        (SELECT record_key FROM publications WHERE mid = :mid ORDER BY pubdate DESC LIMIT 500)""")
    suspend fun capCreator(mid: Long)
}
