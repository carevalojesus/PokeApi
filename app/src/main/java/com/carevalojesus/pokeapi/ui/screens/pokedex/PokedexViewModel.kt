package com.carevalojesus.pokeapi.ui.screens.pokedex

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.repository.PokemonRepository
import com.carevalojesus.pokeapi.domain.model.PokemonItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PokedexViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokemonRepository()
    private val favoritesRepository =
        (application as PokeApiApplication).favoritesRepository

    private var allPokemon: List<PokemonItem> = emptyList()
    private var currentOffset = 0
    private var hasMore = true
    private var isLoadingPage = false

    private val _uiState = MutableStateFlow<PokedexUiState>(PokedexUiState.Loading)
    val uiState: StateFlow<PokedexUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedType = MutableStateFlow<String?>(null)
    val selectedType: StateFlow<String?> = _selectedType

    val favoriteIds: StateFlow<Set<Int>> = favoritesRepository.getAllFavoriteIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val availableTypes = listOf(
        "normal", "fire", "water", "electric", "grass", "ice",
        "fighting", "poison", "ground", "flying", "psychic", "bug",
        "rock", "ghost", "dragon", "dark", "steel", "fairy"
    )

    val typeTranslations = mapOf(
        "normal" to "Normal",
        "fire" to "Fuego",
        "water" to "Agua",
        "electric" to "Eléctrico",
        "grass" to "Planta",
        "ice" to "Hielo",
        "fighting" to "Lucha",
        "poison" to "Veneno",
        "ground" to "Tierra",
        "flying" to "Volador",
        "psychic" to "Psíquico",
        "bug" to "Bicho",
        "rock" to "Roca",
        "ghost" to "Fantasma",
        "dragon" to "Dragón",
        "dark" to "Siniestro",
        "steel" to "Acero",
        "fairy" to "Hada"
    )

    init {
        loadFirstPage()
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            _uiState.value = PokedexUiState.Loading
            try {
                val page = repository.getPokemonPage(offset = 0)
                allPokemon = page.pokemon
                currentOffset = page.pokemon.size
                hasMore = page.hasMore
                applyFilters()
            } catch (e: Exception) {
                _uiState.value = PokedexUiState.Error(
                    e.message ?: "Error desconocido"
                )
            }
        }
    }

    fun loadNextPage() {
        if (isLoadingPage || !hasMore) return
        isLoadingPage = true
        val currentState = _uiState.value
        if (currentState is PokedexUiState.Success) {
            _uiState.value = currentState.copy(isLoadingMore = true)
        }
        viewModelScope.launch {
            try {
                val page = repository.getPokemonPage(offset = currentOffset)
                allPokemon = allPokemon + page.pokemon
                currentOffset += page.pokemon.size
                hasMore = page.hasMore
                applyFilters()
            } catch (_: Exception) {
                if (currentState is PokedexUiState.Success) {
                    _uiState.value = currentState.copy(isLoadingMore = false)
                }
            } finally {
                isLoadingPage = false
            }
        }
    }

    fun loadPokemonList() {
        currentOffset = 0
        hasMore = true
        allPokemon = emptyList()
        loadFirstPage()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun onTypeSelected(type: String?) {
        _selectedType.value = if (_selectedType.value == type) null else type
        applyFilters()
    }

    fun toggleFavorite(pokemonId: Int) {
        viewModelScope.launch {
            if (favoriteIds.value.contains(pokemonId)) {
                favoritesRepository.remove(pokemonId)
            } else {
                favoritesRepository.add(pokemonId)
            }
        }
    }

    private fun applyFilters() {
        val query = _searchQuery.value
        val type = _selectedType.value

        var filtered = allPokemon

        if (query.isNotBlank()) {
            filtered = filtered.filter { pokemon ->
                pokemon.name.contains(query, ignoreCase = true) ||
                    pokemon.id.toString() == query
            }
        }

        if (type != null) {
            filtered = filtered.filter { pokemon ->
                pokemon.types.contains(type)
            }
        }

        _uiState.value = PokedexUiState.Success(
            pokemonList = filtered,
            isLoadingMore = false,
            hasMore = hasMore
        )
    }
}
