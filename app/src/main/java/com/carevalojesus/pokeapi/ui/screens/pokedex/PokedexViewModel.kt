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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PokedexViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokemonRepository()
    private val app = application as PokeApiApplication
    private val favoritesRepository = app.favoritesRepository
    private val ownedPokemonRepository = app.ownedPokemonRepository
    private val unlockRepository = app.unlockRepository

    companion object {
        private val GEN1_IDS = (1..151).toList()
    }

    private var allPokemon: List<PokemonItem> = emptyList()
    private var discoveredIds: Set<Int> = emptySet()
    private var areTypesLoaded = false
    private var isLoadingTypes = false

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
        loadAllGen1()
        observeDiscoveredIds()
    }

    private fun loadAllGen1() {
        viewModelScope.launch {
            _uiState.value = PokedexUiState.Loading
            try {
                allPokemon = repository
                    .getPokemonList(limit = GEN1_IDS.size, offset = 0)
                    .filter { it.id in GEN1_IDS }
                    .sortedBy { it.id }
                areTypesLoaded = false
                applyFilters()
                loadTypesInBackground()
            } catch (e: Exception) {
                _uiState.value = PokedexUiState.Error(
                    e.message ?: "Error desconocido"
                )
            }
        }
    }

    private fun loadTypesInBackground() {
        if (isLoadingTypes || areTypesLoaded || allPokemon.isEmpty()) return
        viewModelScope.launch {
            isLoadingTypes = true
            try {
                allPokemon = repository.fillPokemonTypes(allPokemon)
                areTypesLoaded = true
                applyFilters()
            } finally {
                isLoadingTypes = false
            }
        }
    }

    private fun observeDiscoveredIds() {
        viewModelScope.launch {
            combine(
                ownedPokemonRepository.getAllPokemonIdsFlow(),
                unlockRepository.getAllIdsFlow()
            ) { ownedIds, unlockedIds ->
                (ownedIds + unlockedIds).toSet()
            }.collect { ids ->
                discoveredIds = ids
                // Solo actualizar si ya tenemos datos cargados
                if (allPokemon.isNotEmpty()) {
                    applyFilters()
                }
            }
        }
    }

    fun loadPokemonList() {
        allPokemon = emptyList()
        areTypesLoaded = false
        isLoadingTypes = false
        loadAllGen1()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun onTypeSelected(type: String?) {
        _selectedType.value = if (_selectedType.value == type) null else type
        if (_selectedType.value != null && !areTypesLoaded) {
            loadTypesInBackground()
        }
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
                // Solo buscar por nombre en los descubiertos, por ID en todos
                if (pokemon.id in discoveredIds) {
                    pokemon.name.contains(query, ignoreCase = true) ||
                        pokemon.id.toString() == query
                } else {
                    pokemon.id.toString() == query
                }
            }
        }

        if (type != null && areTypesLoaded) {
            filtered = filtered.filter { pokemon ->
                pokemon.types.contains(type)
            }
        }

        _uiState.value = PokedexUiState.Success(
            pokemonList = filtered,
            discoveredIds = discoveredIds
        )
    }
}
