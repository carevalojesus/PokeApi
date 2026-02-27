package com.carevalojesus.pokeapi.data.repository

import com.carevalojesus.pokeapi.data.local.OwnedPokemonDao
import com.carevalojesus.pokeapi.data.local.OwnedPokemonEntity
import kotlinx.coroutines.flow.Flow

class OwnedPokemonRepository(private val dao: OwnedPokemonDao) {

    fun getAll(): Flow<List<OwnedPokemonEntity>> = dao.getAll()

    fun getAllPokemonIdsFlow(): Flow<List<Int>> = dao.getAllPokemonIdsFlow()

    fun getTradeableOnly(): Flow<List<OwnedPokemonEntity>> = dao.getTradeableOnly()

    suspend fun owns(pokemonId: Int): Boolean = dao.owns(pokemonId)

    suspend fun add(pokemonId: Int, nickname: String = "", isStarter: Boolean = false) {
        dao.insert(
            OwnedPokemonEntity(
                pokemonId = pokemonId,
                nickname = nickname,
                isStarter = isStarter
            )
        )
    }

    suspend fun removeOneByPokemonId(pokemonId: Int): Boolean {
        val entity = dao.getFirstTradeableByPokemonId(pokemonId) ?: return false
        dao.deleteById(entity.id)
        return true
    }
}
