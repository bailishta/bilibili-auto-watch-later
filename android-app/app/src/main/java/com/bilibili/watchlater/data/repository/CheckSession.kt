package com.bilibili.watchlater.data.repository

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CheckTarget(val mid: Long, val name: String)

/** Snapshot the original time window and list; resume never slides the cutoff forward. */
data class CheckSession(
    val uid: String,
    val targets: List<CheckTarget>,
    val windowHours: Int,
    val cutoff: Long,
    val id: String = UUID.randomUUID().toString(),
    val next: Int = 0,
    val videos: List<NewVideo> = emptyList(),
    val errors: Int = 0,
    val historyReady: Boolean = false
) {
    fun encode(): String = JSONObject().apply {
        put("id", id); put("uid", uid); put("windowHours", windowHours); put("cutoff", cutoff)
        put("next", next); put("errors", errors); put("historyReady", historyReady)
        put("targets", JSONArray().apply { targets.forEach { put(JSONObject().put("mid", it.mid).put("name", it.name)) } })
        put("videos", JSONArray().apply { videos.forEach {
            put(JSONObject().put("bvid", it.bvid).put("title", it.title).put("author", it.author)
                .put("mid", it.mid).put("pubdate", it.pubdate))
        } })
    }.toString()

    companion object {
        fun decode(raw: String): CheckSession {
            val json = JSONObject(raw)
            val targets = json.getJSONArray("targets")
            val videos = json.getJSONArray("videos")
            return CheckSession(
                uid = json.getString("uid"), id = json.getString("id"),
                windowHours = json.getInt("windowHours"), cutoff = json.getLong("cutoff"),
                next = json.getInt("next"), errors = json.getInt("errors"), historyReady = json.getBoolean("historyReady"),
                targets = List(targets.length()) { i -> targets.getJSONObject(i).let { CheckTarget(it.getLong("mid"), it.getString("name")) } },
                videos = List(videos.length()) { i -> videos.getJSONObject(i).let {
                    NewVideo(it.getString("bvid"), it.getString("title"), it.getString("author"), it.getLong("mid"), it.getLong("pubdate"))
                } }
            ).also { require(it.next in 0..it.targets.size) }
        }
    }
}
