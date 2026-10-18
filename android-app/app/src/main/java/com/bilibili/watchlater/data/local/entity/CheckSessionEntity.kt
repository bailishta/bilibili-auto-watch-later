package com.bilibili.watchlater.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "check_session")
data class CheckSessionEntity(@PrimaryKey val id: Int = 1, val payload: String)
