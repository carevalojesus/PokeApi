package com.carevalojesus.pokeapi.data.repository

import com.carevalojesus.pokeapi.data.firebase.FirebaseRepository
import com.carevalojesus.pokeapi.data.local.FavoriteDao
import com.carevalojesus.pokeapi.data.local.FavoriteEntity
import kotlinx.coroutines.flow.Flow

class FavoritesRepository(
    private val dao: FavoriteDao,
    private val firebaseRepository: FirebaseRepository
) {

    fun getAllFavoriteIds(): Flow<List<Int>> = dao.getAllFavoriteIds()

    fun isFavorite(pokemonId: Int): Flow<Boolean> = dao.isFavorite(pokemonId)

    suspend fun add(pokemonId: Int) {
        dao.add(FavoriteEntity(pokemonId))
        runCatching { firebaseRepository.addFavorite(pokemonId) }
    }

    suspend fun remove(pokemonId: Int) {
        dao.remove(pokemonId)
        runCatching { firebaseRepository.removeFavorite(pokemonId) }
    }

    suspend fun syncFromFirebase() {
        val remoteFavorites = firebaseRepository.getFavorites().toSet()
        val localFavorites = dao.getAllFavoriteIdsOnce().toSet()

        (remoteFavorites - localFavorites).forEach { pokemonId ->
            dao.add(FavoriteEntity(pokemonId))
        }
        (localFavorites - remoteFavorites).forEach { pokemonId ->
            dao.remove(pokemonId)
        }
    }
}
