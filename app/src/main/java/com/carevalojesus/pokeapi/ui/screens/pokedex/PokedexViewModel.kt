package com.carevalojesus.pokeapi.ui.screens.pokedex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.data.repository.PokemonRepository
import com.carevalojesus.pokeapi.domain.model.PokemonItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PokedexViewModel : ViewModel() {

    private val repository = PokemonRepository()

    private var allPokemon: List<PokemonItem> = emptyList()

    private val _uiState = MutableStateFlow<PokedexUiState>(PokedexUiState.Loading)
    val uiState: StateFlow<PokedexUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        loadPokemonList()
    }

    fun loadPokemonList() {
        viewModelScope.launch {
            _uiState.value = PokedexUiState.Loading
            try {
                allPokemon = repository.getPokemonList()
                filterPokemon(_searchQuery.value)
            } catch (e: Exception) {
                _uiState.value = PokedexUiState.Error(
                    e.message ?: "Error desconocido"
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        filterPokemon(query)
    }

    private fun filterPokemon(query: String) {
        if (query.isBlank()) {
            _uiState.value = PokedexUiState.Success(allPokemon)
        } else {
            val filtered = allPokemon.filter { pokemon ->
                pokemon.name.contains(query, ignoreCase = true) ||
                    pokemon.id.toString() == query
            }
            _uiState.value = PokedexUiState.Success(filtered)
        }
    }
}
