package com.carevalojesus.pokeapi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnedPokemonDao {

    @Query("SELECT * FROM owned_pokemon ORDER BY obtainedAt DESC")
    fun getAll(): Flow<List<OwnedPokemonEntity>>

    @Query("SELECT * FROM owned_pokemon WHERE isStarter = 0 ORDER BY obtainedAt DESC")
    fun getTradeableOnly(): Flow<List<OwnedPokemonEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM owned_pokemon WHERE pokemonId = :pokemonId)")
    suspend fun owns(pokemonId: Int): Boolean

    @Insert
    suspend fun insert(entity: OwnedPokemonEntity)

    @Query("DELETE FROM owned_pokemon WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM owned_pokemon WHERE pokemonId = :pokemonId AND isStarter = 0 LIMIT 1")
    suspend fun getFirstTradeableByPokemonId(pokemonId: Int): OwnedPokemonEntity?

    @Query("SELECT DISTINCT pokemonId FROM owned_pokemon")
    fun getAllPokemonIdsFlow(): Flow<List<Int>>

    @Query("UPDATE owned_pokemon SET isNewFromTrade = 0 WHERE id = :id")
    suspend fun markTradeSeen(id: Int)

    @Query("UPDATE owned_pokemon SET isStarter = 0 WHERE isStarter = 1")
    suspend fun clearAllStarterFlags()

    @Query("UPDATE owned_pokemon SET isStarter = 1 WHERE id = :id")
    suspend fun setStarterFlag(id: Int)

    @Transaction
    suspend fun changeStarterTransaction(newStarterId: Int) {
        clearAllStarterFlags()
        setStarterFlag(newStarterId)
    }

    @Query("SELECT * FROM owned_pokemon WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): OwnedPokemonEntity?

    @Query("SELECT COUNT(*) FROM owned_pokemon WHERE pokemonId = :pokemonId")
    suspend fun countByPokemonId(pokemonId: Int): Int

    @Query("DELETE FROM owned_pokemon")
    suspend fun deleteAll()
}
