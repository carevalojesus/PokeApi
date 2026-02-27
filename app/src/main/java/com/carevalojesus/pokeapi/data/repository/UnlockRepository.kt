package com.carevalojesus.pokeapi.data.repository

import com.carevalojesus.pokeapi.data.local.UnlockedPokemonDao
import com.carevalojesus.pokeapi.data.local.UnlockedPokemonEntity
import kotlinx.coroutines.flow.Flow

class UnlockRepository(private val dao: UnlockedPokemonDao) {

    companion object {
        // Gen 1 Pokemon pool (excluding starters: 1,4,7,25,133)
        val UNLOCK_POOL = listOf(
            2, 3, 5, 6, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            21, 22, 23, 24, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
            37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53,
            54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70,
            71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87,
            88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103,
            104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117,
            118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131,
            132, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146,
            147, 148, 149, 150, 151
        )
    }

    fun getAll(): Flow<List<UnlockedPokemonEntity>> = dao.getAll()

    fun getAllIdsFlow(): Flow<List<Int>> = dao.getAllIdsFlow()

    suspend fun getRandomUnlockable(): Int? {
        val alreadyUnlocked = dao.getAllIds().toSet()
        val available = UNLOCK_POOL.filter { it !in alreadyUnlocked }
        return available.randomOrNull()
    }

    suspend fun unlock(pokemonId: Int) {
        dao.insert(UnlockedPokemonEntity(pokemonId = pokemonId))
    }
}
