package com.carevalojesus.pokeapi

import android.app.Application
import com.carevalojesus.pokeapi.data.local.PokeDatabase
import com.carevalojesus.pokeapi.data.repository.FavoritesRepository
import com.carevalojesus.pokeapi.data.repository.OwnedPokemonRepository
import com.carevalojesus.pokeapi.data.repository.TradeRepository
import com.carevalojesus.pokeapi.data.repository.UnlockRepository
import com.carevalojesus.pokeapi.data.repository.UserRepository

class PokeApiApplication : Application() {

    val database by lazy { PokeDatabase.getInstance(this) }
    val favoritesRepository by lazy { FavoritesRepository(database.favoriteDao()) }
    val userRepository by lazy { UserRepository(database.userProfileDao()) }
    val ownedPokemonRepository by lazy { OwnedPokemonRepository(database.ownedPokemonDao()) }
    val unlockRepository by lazy { UnlockRepository(database.unlockedPokemonDao()) }
    val tradeRepository by lazy { TradeRepository(database.tradeDao()) }
}
