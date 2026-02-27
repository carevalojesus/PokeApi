package com.carevalojesus.pokeapi.ui.screens.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.repository.PokemonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PokemonDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokemonRepository()
    private val favoritesRepository =
        (application as PokeApiApplication).favoritesRepository

    private val _uiState = MutableStateFlow<PokemonDetailUiState>(PokemonDetailUiState.Loading)
    val uiState: StateFlow<PokemonDetailUiState> = _uiState

    private val _pokemonId = MutableStateFlow(0)

    val isFavorite: StateFlow<Boolean> = _pokemonId.flatMapLatest { id ->
        if (id > 0) favoritesRepository.isFavorite(id) else flowOf(false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun loadPokemon(id: Int) {
        _pokemonId.value = id
        viewModelScope.launch {
            _uiState.value = PokemonDetailUiState.Loading
            try {
                val detail = repository.getPokemonDetail(id)
                _uiState.value = PokemonDetailUiState.Success(detail)
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
            }
        }
    }
}
