package com.bilibili.watchlater.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "creators")
data class CreatorEntity(
    @PrimaryKey
    val mid: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "face")
    val face: String = "",

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),

    // ── 排序元数据（v2 新增，可空） ──
    // fans 粉丝数；avgIntervalDays 平均更新间隔（天）；
    // lastPubdate 最近发布时间；metaUpdatedAt 元数据更新时间
    @ColumnInfo(name = "fans")
    val fans: Long? = null,

    @ColumnInfo(name = "avg_interval_days")
    // 保留 v2 列以兼容旧数据库；新的排序与界面不再使用更新频率。
    val avgIntervalDays: Double? = null,

    @ColumnInfo(name = "last_pubdate")
    val lastPubdate: Long? = null,

    @ColumnInfo(name = "meta_updated_at")
    val metaUpdatedAt: Long? = null
)
