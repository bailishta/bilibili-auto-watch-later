package com.bilibili.watchlater.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bilibili.watchlater.data.local.dao.CreatorDao
import com.bilibili.watchlater.data.local.dao.TrackedVideoDao
import com.bilibili.watchlater.data.local.entity.CreatorEntity
import com.bilibili.watchlater.data.local.entity.TrackedVideoEntity
import com.bilibili.watchlater.data.local.entity.PublicationEntity
import com.bilibili.watchlater.data.local.dao.PublicationDao

@Database(
    entities = [CreatorEntity::class, TrackedVideoEntity::class, PublicationEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun creatorDao(): CreatorDao
    abstract fun trackedVideoDao(): TrackedVideoDao
    abstract fun publicationDao(): PublicationDao

    companion object {
        // v1 → v2：creators 表新增排序元数据列（可空，老数据默认 NULL）
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE creators ADD COLUMN fans INTEGER")
                db.execSQL("ALTER TABLE creators ADD COLUMN avg_interval_days REAL")
                db.execSQL("ALTER TABLE creators ADD COLUMN last_pubdate INTEGER")
                db.execSQL("ALTER TABLE creators ADD COLUMN meta_updated_at INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS publications (
                    mid INTEGER NOT NULL, record_key TEXT NOT NULL, bvid TEXT NOT NULL,
                    pubdate INTEGER NOT NULL, PRIMARY KEY(mid, record_key),
                    FOREIGN KEY(mid) REFERENCES creators(mid) ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_publications_mid ON publications(mid)")
                db.execSQL("""INSERT INTO publications(mid, record_key, bvid, pubdate)
                    SELECT mid, 'time:' || last_pubdate, '', last_pubdate FROM creators
                    WHERE last_pubdate > 0""")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bili_watch_later.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
