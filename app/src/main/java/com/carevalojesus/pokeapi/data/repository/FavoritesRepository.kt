package com.carevalojesus.pokeapi.data.repository

import com.carevalojesus.pokeapi.data.local.FavoriteDao
import com.carevalojesus.pokeapi.data.local.FavoriteEntity
import kotlinx.coroutines.flow.Flow

class FavoritesRepository(private val dao: FavoriteDao) {

    fun getAllFavoriteIds(): Flow<List<Int>> = dao.getAllFavoriteIds()

    fun isFavorite(pokemonId: Int): Flow<Boolean> = dao.isFavorite(pokemonId)

    suspend fun add(pokemonId: Int) = dao.add(FavoriteEntity(pokemonId))

    suspend fun remove(pokemonId: Int) = dao.remove(pokemonId)
}
