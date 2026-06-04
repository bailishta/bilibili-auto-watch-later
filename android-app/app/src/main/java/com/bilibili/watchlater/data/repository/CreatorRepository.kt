package com.bilibili.watchlater.data.repository

import com.bilibili.watchlater.data.local.dao.CreatorDao
import com.bilibili.watchlater.data.local.entity.CreatorEntity
import kotlinx.coroutines.flow.Flow

class CreatorRepository(private val dao: CreatorDao) {

    val allCreators: Flow<List<CreatorEntity>> = dao.getAll()

    suspend fun getAll(): List<CreatorEntity> = dao.getAllList()

    suspend fun exists(mid: Long): Boolean = dao.exists(mid)

    suspend fun add(mid: Long, name: String, face: String = "") {
        dao.insert(CreatorEntity(mid = mid, name = name, face = face))
    }

    suspend fun remove(mid: Long) {
        dao.deleteByMid(mid)
    }

    suspend fun count(): Int = dao.count()
}
