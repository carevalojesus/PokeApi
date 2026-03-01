package com.carevalojesus.pokeapi.ui.screens.mypokemon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.firebase.PokemonCareState
import com.carevalojesus.pokeapi.data.local.OwnedPokemonEntity
import com.carevalojesus.pokeapi.data.local.UserProfileEntity
import com.carevalojesus.pokeapi.data.repository.AggregatedPokemon
import com.carevalojesus.pokeapi.ui.notifications.PokemonCareNotifier
import com.carevalojesus.pokeapi.util.PokemonNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyPokemonViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication
    private val ownedPokemonRepository = app.ownedPokemonRepository
    private val userRepository = app.userRepository
    private val firebaseRepository = app.firebaseRepository

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

    private val _careState = MutableStateFlow(PokemonCareUiState())
    val careState: StateFlow<PokemonCareUiState> = _careState

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

    fun loadPokemonCare(pokemonId: Int) {
        viewModelScope.launch {
            _careState.value = _careState.value.copy(isLoading = true, error = null)
            runCatching {
                firebaseRepository.getPokemonCareState(pokemonId)
            }.onSuccess { state ->
                updateCareUiFromState(state)
                PokemonCareNotifier.notifyStateIfNeeded(app, state)
                applyPenaltySideEffects(state)
            }.onFailure { error ->
                _careState.value = _careState.value.copy(
                    isLoading = false,
                    error = error.message ?: "No se pudo cargar el estado de tu Pokémon"
                )
            }
        }
    }

    fun feedPokemon(pokemonId: Int) {
        viewModelScope.launch {
            _careState.value = _careState.value.copy(isLoading = true, error = null)
            runCatching {
                val next = firebaseRepository.feedPokemon(pokemonId)
                awardCarePoints(pokemonId, "feed", 6)
                next
            }.onSuccess { state ->
                updateCareUiFromState(state)
                PokemonCareNotifier.notifyStateIfNeeded(app, state)
                applyPenaltySideEffects(state)
            }.onFailure { error ->
                _careState.value = _careState.value.copy(
                    isLoading = false,
                    error = error.message ?: "No se pudo alimentar a tu Pokémon"
                )
            }
        }
    }

    fun startSleep(pokemonId: Int) {
        viewModelScope.launch {
            _careState.value = _careState.value.copy(isLoading = true, error = null)
            runCatching {
                val next = firebaseRepository.startPokemonSleep(pokemonId)
                awardCarePoints(pokemonId, "sleep_start", 8)
                next
            }.onSuccess { state ->
                updateCareUiFromState(state)
                applyPenaltySideEffects(state)
            }.onFailure { error ->
                _careState.value = _careState.value.copy(
                    isLoading = false,
                    error = error.message ?: "No se pudo poner a dormir a tu Pokémon"
                )
            }
        }
    }

    fun wakePokemon(pokemonId: Int) {
        viewModelScope.launch {
            _careState.value = _careState.value.copy(isLoading = true, error = null)
            runCatching {
                val next = firebaseRepository.wakePokemon(pokemonId)
                awardCarePoints(pokemonId, "wake", 5)
                next
            }.onSuccess { state ->
                updateCareUiFromState(state)
                applyPenaltySideEffects(state)
            }.onFailure { error ->
                _careState.value = _careState.value.copy(
                    isLoading = false,
                    error = error.message ?: "No se pudo despertar a tu Pokémon"
                )
            }
        }
    }

    fun clearCareError() {
        _careState.value = _careState.value.copy(error = null)
    }

    private suspend fun awardCarePoints(pokemonId: Int, action: String, points: Int) {
        val key = when (action) {
            "feed" -> "pet_feed_${pokemonId}_${hourKey()}"
            "sleep_start" -> "pet_sleep_${pokemonId}_${dayKey()}"
            "wake" -> "pet_wake_${pokemonId}_${dayKey()}"
            else -> "pet_misc_${pokemonId}_${dayKey()}"
        }
        runCatching {
            val award = firebaseRepository.awardMissionPoints(
                eventKey = key,
                category = "pet_care",
                points = points
            )
            userRepository.setLocalPoints(award.totalPoints)
            if (award.awardedPoints > 0) {
                _careState.value = _careState.value.copy(
                    lastAwardedPoints = award.awardedPoints,
                    totalPoints = award.totalPoints
                )
            }
        }
    }

    private fun updateCareUiFromState(state: PokemonCareState) {
        val name = PokemonNames.getName(state.pokemonId)
        _careState.value = _careState.value.copy(
            isLoading = false,
            state = state,
            aiMessage = buildPokemonCareMessage(name, state),
            error = null
        )
    }

    private suspend fun applyPenaltySideEffects(state: PokemonCareState) {
        if (state.lastPenaltyPoints > 0) {
            userRepository.syncPointsFromRemote()
        }
        if (state.pokemonLost) {
            ownedPokemonRepository.removeOneByPokemonId(state.pokemonId)
        }
    }

    private fun dayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    private fun hourKey(): String = SimpleDateFormat("yyyyMMddHH", Locale.US).format(Date())
}

sealed interface StarterChangeResult {
    data class Success(val changesRemaining: Int) : StarterChangeResult
    data object NoChangesLeft : StarterChangeResult
    data object Error : StarterChangeResult
}

data class PokemonCareUiState(
    val isLoading: Boolean = false,
    val state: PokemonCareState? = null,
    val aiMessage: String = "",
    val error: String? = null,
    val lastAwardedPoints: Int = 0,
    val totalPoints: Int = 0
)

internal fun buildPokemonCareMessage(name: String, state: PokemonCareState): String {
    return when {
        state.pokemonLost ->
            "$name: Me descuidaste... perdimos una copia del Pokémon."
        state.lastPenaltyPoints > 0 ->
            "$name: Por descuido se perdieron ${state.lastPenaltyPoints} puntos."
        state.sleeping && state.wantsToWakeUp -> "$name: Ya descansé, quiero despertar."
        state.sleeping -> "$name: Estoy durmiendo... zZz."
        state.hunger <= 20 -> "$name: Tengo mucha hambre."
        state.hunger <= 40 -> "$name: Me provoca comer algo."
        state.energy <= 20 -> "$name: Estoy muy cansado, quiero dormir."
        state.energy <= 40 -> "$name: Tengo algo de sueño."
        state.hunger >= 85 && state.energy >= 80 && state.happiness >= 80 ->
            "$name: Estoy lleno, feliz y con energía."
        state.happiness <= 25 -> "$name: Estoy triste, ¿jugamos un rato?"
        else -> "$name: Estoy bien, sigamos entrenando."
    }
}
