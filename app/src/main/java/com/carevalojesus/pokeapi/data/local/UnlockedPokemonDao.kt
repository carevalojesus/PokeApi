package com.carevalojesus.pokeapi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UnlockedPokemonDao {

    @Query("SELECT * FROM unlocked_pokemon ORDER BY unlockedAt DESC")
    fun getAll(): Flow<List<UnlockedPokemonEntity>>

    @Query("SELECT pokemonId FROM unlocked_pokemon")
    suspend fun getAllIds(): List<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: UnlockedPokemonEntity)

    @Query("SELECT pokemonId FROM unlocked_pokemon")
    fun getAllIdsFlow(): Flow<List<Int>>
}
