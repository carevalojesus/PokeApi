package com.carevalojesus.pokeapi

import android.app.Application
import com.carevalojesus.pokeapi.data.local.PokeDatabase
import com.carevalojesus.pokeapi.data.repository.FavoritesRepository

class PokeApiApplication : Application() {

    val database by lazy { PokeDatabase.getInstance(this) }
    val favoritesRepository by lazy { FavoritesRepository(database.favoriteDao()) }
}
