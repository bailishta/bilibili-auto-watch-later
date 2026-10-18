package com.bilibili.watchlater

import android.app.Application
import android.app.ActivityManager
import android.os.Build
import com.bilibili.watchlater.util.AppLogger
import com.bilibili.watchlater.data.local.AppDatabase
import com.bilibili.watchlater.data.remote.BiliApiService
import com.bilibili.watchlater.data.remote.CookieProvider
import com.bilibili.watchlater.data.repository.CheckRepository
import com.bilibili.watchlater.data.repository.CreatorRepository
import com.bilibili.watchlater.data.repository.SettingsRepository
import com.bilibili.watchlater.data.repository.ActivityRepository
import com.bilibili.watchlater.data.repository.BackupRepository
import com.bilibili.watchlater.service.CheckController
import com.bilibili.watchlater.service.CheckNotifications
import com.bilibili.watchlater.util.hasNetworkConnection

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
    lateinit var checkController: CheckController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogger.i("CheckLifecycle", "进程启动 pid=${android.os.Process.myPid()}，版本=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        CheckNotifications.migrateAppearance(this)
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(packageName, 0, 1)
                    .firstOrNull()?.let { AppLogger.i("CheckLifecycle", "上次进程退出：reason=${it.reason}，时间=${it.timestamp}，importance=${it.importance}") }
            } catch (error: Exception) { AppLogger.w("CheckLifecycle", "无法读取上次进程退出原因", error) }
        }

        database = AppDatabase.getInstance(this)
        cookieProvider = CookieProvider(this)
        api = BiliApiService(cookieProvider)
        creatorRepo = CreatorRepository(database.creatorDao())
        settingsRepo = SettingsRepository(this)
        activityRepo = ActivityRepository(database, settingsRepo)
        backupRepo = BackupRepository(database, settingsRepo)
        checkRepo = CheckRepository(api, database.creatorDao(), database.trackedVideoDao(), settingsRepo, activityRepo, database.watchedVideoDao(), database, { hasNetworkConnection() })
        checkController = CheckController(this)
    }

    companion object {
        lateinit var instance: BiliApp
            private set
    }
}
