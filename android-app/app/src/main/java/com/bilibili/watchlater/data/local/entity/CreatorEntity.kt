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
    val addedAt: Long = System.currentTimeMillis()
)
