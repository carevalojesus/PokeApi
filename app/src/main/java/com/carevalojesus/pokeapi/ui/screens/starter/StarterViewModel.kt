package com.carevalojesus.pokeapi.ui.screens.starter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    fun selectStarter(starter: StarterOption) {
        _selectedStarter.value = starter
        _nickname.value = ""
    }

    fun onNicknameChange(value: String) {
        _nickname.value = value
    }

    fun confirmStarter(onComplete: () -> Unit) {
        val starter = _selectedStarter.value ?: return
        if (_isConfirming.value) return
        _isConfirming.value = true
        val finalNickname = _nickname.value.ifBlank { starter.name }
        viewModelScope.launch {
            userRepository.ensureProfileExists()
            userRepository.setStarter(starter.pokemonId)
            ownedPokemonRepository.add(
                pokemonId = starter.pokemonId,
                nickname = finalNickname,
                isStarter = true
            )
            val ownedCount = ownedPokemonRepository.getAll().first().size
            val unlockedCount = unlockRepository.getAll().first().size
            app.firebaseRepository.syncTrainerStats(ownedCount, unlockedCount)
            onComplete()
        }
    }
}
