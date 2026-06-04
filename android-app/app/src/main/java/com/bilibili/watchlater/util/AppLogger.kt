package com.bilibili.watchlater.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AppLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val time: Instant,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    ) {
        fun formatted(): String {
            val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
            val levelStr = when (level) {
                Level.DEBUG -> "D"
                Level.INFO  -> "I"
                Level.WARN  -> "W"
                Level.ERROR -> "E"
            }
            return "${fmt.format(time)} $levelStr/$tag: $message"
        }
    }

    private const val MAX_ENTRIES = 1000
    private val _entries = mutableListOf<Entry>()

    val entries: List<Entry>
        @Synchronized get() = _entries.toList()

    fun clear() {
        synchronized(_entries) { _entries.clear() }
    }

    @Synchronized
    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        val entry = Entry(Instant.now(), level, tag, message, throwable)
        _entries.add(entry)
        if (_entries.size > MAX_ENTRIES) {
            _entries.removeAt(0)
        }

        val logcatMsg = if (throwable != null) {
            "$message\n${android.util.Log.getStackTraceString(throwable)}"
        } else message

        when (level) {
            Level.DEBUG -> android.util.Log.d(tag, logcatMsg)
            Level.INFO  -> android.util.Log.i(tag, logcatMsg)
            Level.WARN  -> android.util.Log.w(tag, logcatMsg)
            Level.ERROR -> android.util.Log.e(tag, logcatMsg)
        }
    }

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(Level.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(Level.ERROR, tag, message, throwable)
}
