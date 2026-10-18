package com.bilibili.watchlater.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo

// 记录 BVID 和最近观看时间；账号之间隔离，按检查范围清理。
@Entity(tableName = "watched_videos", primaryKeys = ["uid", "bvid"])
data class WatchedVideoEntity(
    val uid: String,
    val bvid: String,
    @ColumnInfo(name = "view_at", defaultValue = "0")
    val viewAt: Long
)
