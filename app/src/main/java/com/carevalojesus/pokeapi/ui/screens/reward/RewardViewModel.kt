package com.carevalojesus.pokeapi.ui.screens.reward

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.firebase.ClaimRewardResult
import com.carevalojesus.pokeapi.util.PokemonNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLDecoder

sealed interface RewardUiState {
    data object Idle : RewardUiState
    data object Loading : RewardUiState
    data class Success(val rewardIds: List<Int>, val names: List<String>) : RewardUiState
    data object AlreadyClaimed : RewardUiState
    data class Error(val message: String) : RewardUiState
}

class RewardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication
    private val firebaseRepository = app.firebaseRepository
    private val ownedPokemonRepository = app.ownedPokemonRepository
    private val unlockRepository = app.unlockRepository

    private val _uiState = MutableStateFlow<RewardUiState>(RewardUiState.Idle)
    val uiState: StateFlow<RewardUiState> = _uiState

    fun claimFromPayload(payloadEncoded: String) {
        viewModelScope.launch {
            val payload = runCatching { URLDecoder.decode(payloadEncoded, "UTF-8") }.getOrNull()
                ?: payloadEncoded
            val campaignId = parseCampaignId(payload)
            if (campaignId.isNullOrBlank()) {
                _uiState.value = RewardUiState.Error("QR invalido")
                return@launch
            }

            _uiState.value = RewardUiState.Loading
            when (val result = firebaseRepository.claimRewardFromCampaign(campaignId)) {
                is ClaimRewardResult.Success -> {
                    result.rewardIds.forEach { id ->
                        ownedPokemonRepository.add(
                            pokemonId = id,
                            nickname = PokemonNames.getName(id),
                            obtainedVia = "qr_reward"
                        )
                        unlockRepository.unlock(id)
                    }
                    val ownedCount = ownedPokemonRepository.getAll().first().size
                    val unlockedCount = unlockRepository.getAll().first().size
                    app.firebaseRepository.syncTrainerStats(ownedCount, unlockedCount)
                    _uiState.value = RewardUiState.Success(
                        rewardIds = result.rewardIds,
                        names = result.rewardIds.map { PokemonNames.getName(it) }
                    )
                }

                ClaimRewardResult.AlreadyClaimed -> {
                    _uiState.value = RewardUiState.AlreadyClaimed
                }

                is ClaimRewardResult.Error -> {
                    _uiState.value = RewardUiState.Error(result.message)
                }
            }
        }
    }

    fun reset() {
        _uiState.value = RewardUiState.Idle
    }

    private fun parseCampaignId(payload: String): String? {
        return if (payload.startsWith("pokeapi://reward")) {
            Uri.parse(payload).getQueryParameter("campaignId")
        } else {
            payload.takeIf { it.isNotBlank() }
        }
    }
}
