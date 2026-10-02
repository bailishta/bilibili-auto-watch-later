package com.bilibili.watchlater.ui.home

import com.bilibili.watchlater.data.local.entity.PublicationEntity
import com.bilibili.watchlater.data.repository.currentWeekStart
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CreatorUi(
    val mid: Long,
    val name: String,
    val face: String = "",
    val fans: Long? = null,
    val lastPubdate: Long? = null
)

data class ActivityDay(
    val date: LocalDate,
    val mids: Set<Long>,
    val minutes: Map<Int, List<CreatorUi>>
)

data class ActivityWeek(val days: List<ActivityDay>, val total: Int)

fun summarizeActivity(
    creators: List<CreatorUi>, records: List<PublicationEntity>,
    start: LocalDate = currentWeekStart(), now: Long = Instant.now().epochSecond
): ActivityWeek {
    val zone = ZoneId.systemDefault()
    val from = start.atStartOfDay(zone).toEpochSecond()
    val to = start.plusDays(7).atStartOfDay(zone).toEpochSecond()
    val names = creators.associateBy { it.mid }
    val buckets = List(7) { sortedMapOf<Int, MutableSet<Long>>() }
    val publications = records.map { it.mid to it.pubdate } + creators.mapNotNull { c -> c.lastPubdate?.let { c.mid to it } }
    for ((mid, date) in publications) {
        if (mid !in names || date < from || date >= to || date > now) continue
        val local = Instant.ofEpochSecond(date).atZone(zone)
        val index = java.time.temporal.ChronoUnit.DAYS.between(start, local.toLocalDate()).toInt()
        buckets[index].getOrPut(local.hour * 60 + local.minute) { linkedSetOf() }.add(mid)
    }
    val days = buckets.mapIndexed { index, minutes ->
        ActivityDay(start.plusDays(index.toLong()), minutes.values.flatten().toSet(),
            minutes.mapValues { (_, mids) -> mids.mapNotNull { names[it] } })
    }
    return ActivityWeek(days, days.flatMap { it.mids }.toSet().size)
}

fun sortCreators(list: List<CreatorUi>, mode: String): List<CreatorUi> {
    if (mode == "default") return list
    val byFans = mode.startsWith("fans-")
    val descending = mode.endsWith("desc")
    return list.sortedWith(Comparator { a, b ->
        val x = if (byFans) a.fans else a.lastPubdate?.takeIf { it > 0 }
        val y = if (byFans) b.fans else b.lastPubdate?.takeIf { it > 0 }
        when {
            x == null && y == null -> 0
            x == null -> 1
            y == null -> -1
            descending -> y.compareTo(x)
            else -> x.compareTo(y)
        }
    })
}
