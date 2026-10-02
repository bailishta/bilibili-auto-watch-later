package com.bilibili.watchlater.data.repository

import androidx.room.withTransaction
import com.bilibili.watchlater.data.local.AppDatabase
import com.bilibili.watchlater.data.local.entity.PublicationEntity
import com.bilibili.watchlater.data.remote.BiliVideo
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

fun currentWeekStart(date: LocalDate = LocalDate.now()): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun publicationCutoff(weeks: Int, now: Long = System.currentTimeMillis()): Long {
    val zone = ZoneId.systemDefault()
    return currentWeekStart(Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
        .minusWeeks((weeks.coerceIn(1, 52) - 1).toLong()).atStartOfDay(zone).toEpochSecond()
}

class ActivityRepository(private val db: AppDatabase, private val settings: SettingsRepository) {
    val records = db.publicationDao().observeAll()

    suspend fun recordMeta(mid: Long, fans: Long?, videos: List<BiliVideo>) {
        val now = System.currentTimeMillis()
        val valid = videos.filter { it.pubdate in 1..(now / 1000) }
        if (fans == null && valid.isEmpty()) return
        db.withTransaction {
            val updated = db.creatorDao().updateMeta(mid, fans, valid.maxOfOrNull { it.pubdate }, now)
            if (updated == 0) return@withTransaction
            val cutoff = publicationCutoff(settings.historyWeeks, now)
            db.publicationDao().insertAll(valid.filter { it.pubdate >= cutoff }.map {
                PublicationEntity(mid, it.bvid.ifBlank { "time:${it.pubdate}" }, it.bvid, it.pubdate)
            })
            db.publicationDao().prune(cutoff, now / 1000)
            db.publicationDao().capCreator(mid)
        }
    }

    suspend fun prune(weeks: Int = settings.historyWeeks) {
        val now = System.currentTimeMillis()
        db.publicationDao().prune(publicationCutoff(weeks, now), now / 1000)
    }
}
