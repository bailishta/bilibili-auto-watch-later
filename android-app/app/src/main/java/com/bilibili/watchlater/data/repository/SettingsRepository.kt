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
        get() = prefs.getInt("new_video_window_hours", 24).takeIf { it in WINDOW_HOURS } ?: 24
        set(value) { require(value in WINDOW_HOURS); prefs.edit().putInt("new_video_window_hours", value).apply() }

    var importDone: Boolean
        get() = prefs.getBoolean("import_done", false)
        set(value) = prefs.edit().putBoolean("import_done", value).apply()

    var sortMode: String
        get() = when (val mode = prefs.getString("sort_mode", "default")) {
            "freq-desc" -> "updated-desc"
            "freq-asc" -> "updated-asc"
            in SORT_MODES -> mode!!
            else -> "default"
        }
        set(value) { require(value in SORT_MODES); prefs.edit().putString("sort_mode", value).apply() }

    var historyWeeks: Int
        get() = prefs.getInt("history_weeks", 3).coerceIn(1, 52)
        set(value) { require(value in 1..52); prefs.edit().putInt("history_weeks", value).apply() }

    var themeMode: String
        get() = prefs.getString("theme_mode", "system").takeIf { it in THEME_MODES } ?: "system"
        set(value) { require(value in THEME_MODES); prefs.edit().putString("theme_mode", value).apply() }

    fun configuration() = AppConfiguration(newVideoWindowHours, historyWeeks, sortMode, importDone, themeMode)

    fun restoreConfiguration(value: AppConfiguration) {
        require(value.newVideoWindowHours in WINDOW_HOURS && value.historyWeeks in 1..52)
        require(value.sortMode in SORT_MODES && value.themeMode in THEME_MODES)
        check(prefs.edit().putInt("new_video_window_hours", value.newVideoWindowHours)
            .putInt("history_weeks", value.historyWeeks).putString("sort_mode", value.sortMode)
            .putBoolean("import_done", value.importDone).putString("theme_mode", value.themeMode).commit()) {
            "保存配置失败，请检查设备存储空间"
        }
    }

    companion object {
        val WINDOW_HOURS = listOf(12, 24, 48, 72, 168)
        val SORT_MODES = setOf("default", "fans-desc", "fans-asc", "updated-desc", "updated-asc")
        val THEME_MODES = setOf("system", "light", "dark")
    }
}

data class AppConfiguration(
    val newVideoWindowHours: Int = 24,
    val historyWeeks: Int = 3,
    val sortMode: String = "default",
    val importDone: Boolean = false,
    val themeMode: String = "system"
)
