package com.carevalojesus.pokeapi.ui.screens.mypokemon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.local.OwnedPokemonEntity
import kotlinx.coroutines.flow.Flow

class MyPokemonViewModel(application: Application) : AndroidViewModel(application) {

    private val ownedPokemonRepository =
        (application as PokeApiApplication).ownedPokemonRepository

    val ownedPokemon: Flow<List<OwnedPokemonEntity>> = ownedPokemonRepository.getAll()
}
