package com.carevalojesus.pokeapi.ui.screens.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.repository.PokemonRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UnlockEvent(
    val pokemonId: Int,
    val pokemonName: String,
    val imageUrl: String
)

class PokemonDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokemonRepository()
    private val app = application as PokeApiApplication
    private val favoritesRepository = app.favoritesRepository
    private val missionRepository = app.missionRepository

    private val _uiState = MutableStateFlow<PokemonDetailUiState>(PokemonDetailUiState.Loading)
    val uiState: StateFlow<PokemonDetailUiState> = _uiState

    private val _pokemonId = MutableStateFlow(0)

    val isFavorite: StateFlow<Boolean> = _pokemonId.flatMapLatest { id ->
        if (id > 0) favoritesRepository.isFavorite(id) else flowOf(false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _unlockEvent = MutableSharedFlow<UnlockEvent>()
    val unlockEvent: SharedFlow<UnlockEvent> = _unlockEvent

    fun loadPokemon(id: Int) {
        _pokemonId.value = id
        viewModelScope.launch {
            _uiState.value = PokemonDetailUiState.Loading
            try {
                val detail = repository.getPokemonDetail(id)
                _uiState.value = PokemonDetailUiState.Success(detail)

                val missionResult = missionRepository.onPokemonViewed(id)
                missionResult.unlockedPokemonIds.forEach { unlockId ->
                    val unlockDetail = try {
                        repository.getPokemonDetail(unlockId)
                    } catch (_: Exception) {
                        null
                    }
                    _unlockEvent.emit(
                        UnlockEvent(
                            pokemonId = unlockId,
                            pokemonName = unlockDetail?.name ?: "Pokémon #$unlockId",
                            imageUrl = unlockDetail?.imageUrl
                                ?: "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/$unlockId.png"
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.value = PokemonDetailUiState.Error(
                    e.message ?: "Error desconocido"
                )
            }
        }
    }

    fun toggleFavorite() {
        val id = _pokemonId.value
        if (id <= 0) return
        viewModelScope.launch {
            if (isFavorite.value) {
                favoritesRepository.remove(id)
            } else {
                favoritesRepository.add(id)
                missionRepository.onPokemonFavorited(id)
            }
        }
    }
}
