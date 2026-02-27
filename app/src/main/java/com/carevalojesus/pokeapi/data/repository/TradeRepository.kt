package com.carevalojesus.pokeapi.data.repository

import com.carevalojesus.pokeapi.data.local.TradeDao
import com.carevalojesus.pokeapi.data.local.TradeEntity
import kotlinx.coroutines.flow.Flow

class TradeRepository(private val dao: TradeDao) {

    fun getAll(): Flow<List<TradeEntity>> = dao.getAll()

    suspend fun getById(tradeId: String): TradeEntity? = dao.getById(tradeId)

    suspend fun insert(entity: TradeEntity) = dao.insert(entity)

    suspend fun update(entity: TradeEntity) = dao.update(entity)
}
