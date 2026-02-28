package com.carevalojesus.pokeapi.ui.screens.mypokemon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.local.OwnedPokemonEntity
import com.carevalojesus.pokeapi.data.local.UserProfileEntity
import com.carevalojesus.pokeapi.data.repository.AggregatedPokemon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MyPokemonViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication
    private val ownedPokemonRepository = app.ownedPokemonRepository
    private val userRepository = app.userRepository

    val ownedPokemon: StateFlow<List<OwnedPokemonEntity>> = ownedPokemonRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aggregatedPokemon: StateFlow<List<AggregatedPokemon>> = ownedPokemonRepository.getAll()
        .map { list ->
            list.groupBy { it.pokemonId }
                .map { (pokemonId, entities) ->
                    AggregatedPokemon(
                        pokemonId = pokemonId,
                        count = entities.size,
                        isStarter = entities.any { it.isStarter },
                        nickname = entities.firstOrNull { it.isStarter }?.nickname
                            ?: entities.first().nickname,
                        hasNewFromTrade = entities.any { it.isNewFromTrade },
                        representativeId = entities.first().id
                    )
                }
                .sortedWith(compareByDescending<AggregatedPokemon> { it.isStarter }.thenBy { it.pokemonId })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profile: StateFlow<UserProfileEntity?> = userRepository.getProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
            val profileChanged = userRepository.changeStarter(pokemonId)
            if (!profileChanged) {
                _starterChangeResult.value = StarterChangeResult.Error
                return@launch
            }

            val dbChanged = ownedPokemonRepository.changeStarter(entityId)
            if (dbChanged) {
                _starterChangeResult.value = StarterChangeResult.Success((remaining - 1).coerceAtLeast(0))
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
