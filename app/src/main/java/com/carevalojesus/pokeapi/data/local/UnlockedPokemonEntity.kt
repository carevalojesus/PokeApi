package com.carevalojesus.pokeapi.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unlocked_pokemon")
data class UnlockedPokemonEntity(
    @PrimaryKey val pokemonId: Int,
    val unlockedAt: Long = System.currentTimeMillis()
)
