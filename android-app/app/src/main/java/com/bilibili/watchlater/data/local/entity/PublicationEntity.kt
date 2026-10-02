package com.bilibili.watchlater.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "publications",
    primaryKeys = ["mid", "record_key"],
    foreignKeys = [ForeignKey(
        entity = CreatorEntity::class,
        parentColumns = ["mid"], childColumns = ["mid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("mid")]
)
data class PublicationEntity(
    val mid: Long,
    @ColumnInfo(name = "record_key") val recordKey: String,
    val bvid: String = "",
    val pubdate: Long
)
