package com.carevalojesus.pokeapi.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "owned_pokemon")
data class OwnedPokemonEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pokemonId: Int,
    val nickname: String = "",
    val obtainedAt: Long = System.currentTimeMillis(),
    val isStarter: Boolean = false
)
