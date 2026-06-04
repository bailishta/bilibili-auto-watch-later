package com.bilibili.watchlater.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracked_videos")
data class TrackedVideoEntity(
    @PrimaryKey
    val bvid: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "mid")
    val mid: Long = 0,

    @ColumnInfo(name = "author")
    val author: String = "",

    @ColumnInfo(name = "pubdate")
    val pubdate: Long = 0,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
