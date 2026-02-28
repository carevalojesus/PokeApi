package com.carevalojesus.pokeapi.ui.screens.mypokemon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.local.OwnedPokemonEntity
import com.carevalojesus.pokeapi.data.local.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MyPokemonViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication
    private val ownedPokemonRepository = app.ownedPokemonRepository
    private val userRepository = app.userRepository

    val ownedPokemon: Flow<List<OwnedPokemonEntity>> = ownedPokemonRepository.getAll()
    val profile: Flow<UserProfileEntity?> = userRepository.getProfile()

    private val _starterChangeResult = MutableStateFlow<StarterChangeResult?>(null)
    val starterChangeResult: StateFlow<StarterChangeResult?> = _starterChangeResult

    fun markTradeSeen(entityId: Int) {
        viewModelScope.launch {
            ownedPokemonRepository.markTradeSeen(entityId)
        }
    }

    fun changeStarter(entityId: Int, pokemonId: Int) {
        viewModelScope.launch {
            val remaining = userRepository.getStarterChangesRemaining()
            if (remaining <= 0) {
                _starterChangeResult.value = StarterChangeResult.NoChangesLeft
                return@launch
            }
            val dbChanged = ownedPokemonRepository.changeStarter(entityId)
            if (dbChanged) {
                userRepository.changeStarter(pokemonId)
                _starterChangeResult.value = StarterChangeResult.Success(remaining - 1)
            } else {
                _starterChangeResult.value = StarterChangeResult.Error
            }
        }
    }

    fun clearStarterChangeResult() {
        _starterChangeResult.value = null
    }
}

sealed interface StarterChangeResult {
    data class Success(val changesRemaining: Int) : StarterChangeResult
    data object NoChangesLeft : StarterChangeResult
    data object Error : StarterChangeResult
}
