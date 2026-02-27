package com.carevalojesus.pokeapi.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.data.repository.PokemonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PokemonDetailViewModel : ViewModel() {

    private val repository = PokemonRepository()

    private val _uiState = MutableStateFlow<PokemonDetailUiState>(PokemonDetailUiState.Loading)
    val uiState: StateFlow<PokemonDetailUiState> = _uiState

    fun loadPokemon(id: Int) {
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
}
