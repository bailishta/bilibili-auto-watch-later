package com.bilibili.watchlater

import android.app.Application
import com.bilibili.watchlater.data.local.AppDatabase
import com.bilibili.watchlater.data.remote.BiliApiService
import com.bilibili.watchlater.data.remote.CookieProvider
import com.bilibili.watchlater.data.repository.CheckRepository
import com.bilibili.watchlater.data.repository.CreatorRepository
import com.bilibili.watchlater.data.repository.SettingsRepository
import com.bilibili.watchlater.data.repository.ActivityRepository
import com.bilibili.watchlater.data.repository.BackupRepository

class BiliApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var cookieProvider: CookieProvider
        private set
    lateinit var api: BiliApiService
        private set
    lateinit var creatorRepo: CreatorRepository
        private set
    lateinit var settingsRepo: SettingsRepository
        private set
    lateinit var checkRepo: CheckRepository
        private set
    lateinit var activityRepo: ActivityRepository
        private set
    lateinit var backupRepo: BackupRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        cookieProvider = CookieProvider(this)
        api = BiliApiService(cookieProvider)
        creatorRepo = CreatorRepository(database.creatorDao())
        settingsRepo = SettingsRepository(this)
        activityRepo = ActivityRepository(database, settingsRepo)
        backupRepo = BackupRepository(database, settingsRepo)
        checkRepo = CheckRepository(api, database.creatorDao(), database.trackedVideoDao(), settingsRepo, activityRepo)
    }

    companion object {
        lateinit var instance: BiliApp
            private set
    }
}
