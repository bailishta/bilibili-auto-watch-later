package com.bilibili.watchlater.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bilibili.watchlater.data.local.dao.CreatorDao
import com.bilibili.watchlater.data.local.dao.TrackedVideoDao
import com.bilibili.watchlater.data.local.entity.CreatorEntity
import com.bilibili.watchlater.data.local.entity.TrackedVideoEntity

@Database(
    entities = [CreatorEntity::class, TrackedVideoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun creatorDao(): CreatorDao
    abstract fun trackedVideoDao(): TrackedVideoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bili_watch_later.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
