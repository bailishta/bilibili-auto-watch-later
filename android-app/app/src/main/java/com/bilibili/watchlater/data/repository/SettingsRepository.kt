package com.bilibili.watchlater.data.repository

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var totalAdded: Int
        get() = prefs.getInt("total_added", 0)
        set(value) = prefs.edit().putInt("total_added", value).apply()

    var lastCheck: Long
        get() = prefs.getLong("last_check", 0)
        set(value) = prefs.edit().putLong("last_check", value).apply()

    var lastNewCount: Int
        get() = prefs.getInt("last_new_count", 0)
        set(value) = prefs.edit().putInt("last_new_count", value).apply()

    var newVideoWindowHours: Int
        get() = prefs.getInt("new_video_window_hours", 24)
        set(value) = prefs.edit().putInt("new_video_window_hours", value).apply()

    var importDone: Boolean
        get() = prefs.getBoolean("import_done", false)
        set(value) = prefs.edit().putBoolean("import_done", value).apply()
}
