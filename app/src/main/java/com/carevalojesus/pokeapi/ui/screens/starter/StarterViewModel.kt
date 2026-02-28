package com.carevalojesus.pokeapi.ui.screens.starter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class StarterOption(
    val pokemonId: Int,
    val name: String,
    val imageUrl: String
)

class StarterViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication
    private val userRepository = app.userRepository
    private val ownedPokemonRepository = app.ownedPokemonRepository
    private val unlockRepository = app.unlockRepository

    val starters = listOf(
        StarterOption(1, "Bulbasaur", "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/1.png"),
        StarterOption(4, "Charmander", "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/4.png"),
        StarterOption(7, "Squirtle", "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/7.png"),
        StarterOption(25, "Pikachu", "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/25.png"),
        StarterOption(133, "Eevee", "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/133.png")
    )

    private val _selectedStarter = MutableStateFlow<StarterOption?>(null)
    val selectedStarter: StateFlow<StarterOption?> = _selectedStarter

    private val _nickname = MutableStateFlow("")
    val nickname: StateFlow<String> = _nickname

    private val _isConfirming = MutableStateFlow(false)
    val isConfirming: StateFlow<Boolean> = _isConfirming

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun clearError() { _errorMessage.value = null }

    fun selectStarter(starter: StarterOption) {
        _selectedStarter.value = starter
        _nickname.value = ""
        _errorMessage.value = null
    }

    fun onNicknameChange(value: String) {
        _nickname.value = value
        _errorMessage.value = null
    }

    fun confirmStarter(onComplete: () -> Unit) {
        val starter = _selectedStarter.value ?: return
        if (_isConfirming.value) return
        _isConfirming.value = true
        val finalNickname = _nickname.value.ifBlank { starter.name }
        viewModelScope.launch {
            try {
                val starterSaved = withTimeoutOrNull(20_000L) {
                    userRepository.ensureProfileExists()
                    userRepository.setStarter(starter.pokemonId)
                }
                if (starterSaved == null) {
                    _errorMessage.value = "No se pudo confirmar tu starter. Verifica tu conexión."
                    _isConfirming.value = false
                    return@launch
                }

                ownedPokemonRepository.add(
                    pokemonId = starter.pokemonId,
                    nickname = finalNickname,
                    isStarter = true
                )
                onComplete()

                // Sincronización secundaria en background: no debe bloquear la navegación.
                runCatching {
                    app.firebaseRepository.ensureInventoryHasAtLeast(
                        pokemonId = starter.pokemonId,
                        minCount = 1
                    )
                    val ownedCount = ownedPokemonRepository.getAll().first().size
                    val unlockedCount = unlockRepository.getAll().first().size
                    val points = withTimeoutOrNull(10_000L) { userRepository.getPoints() } ?: 0
                    app.firebaseRepository.syncTrainerStats(ownedCount, unlockedCount, points)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Error al elegir tu starter"
                _isConfirming.value = false
            }
        }
    }
}
