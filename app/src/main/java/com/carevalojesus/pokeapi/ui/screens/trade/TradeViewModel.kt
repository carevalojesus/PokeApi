package com.carevalojesus.pokeapi.ui.screens.trade

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.local.OwnedPokemonEntity
import com.carevalojesus.pokeapi.data.local.TradeEntity
import com.carevalojesus.pokeapi.domain.model.TradeOffer
import com.carevalojesus.pokeapi.util.PokemonNames
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface TradeUiState {
    data object Idle : TradeUiState
    data object Creating : TradeUiState
    data class QrGenerated(val bitmap: Bitmap, val tradeOffer: TradeOffer) : TradeUiState
    data class TradeSuccess(
        val receivedPokemonId: Int,
        val receivedPokemonName: String
    ) : TradeUiState
    data class Error(val message: String) : TradeUiState
}

class TradeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication
    private val userRepository = app.userRepository
    private val ownedPokemonRepository = app.ownedPokemonRepository
    private val unlockRepository = app.unlockRepository
    private val tradeRepository = app.tradeRepository
    private val missionRepository = app.missionRepository

    private val gson = Gson()

    val tradeablePokemon: Flow<List<OwnedPokemonEntity>> = ownedPokemonRepository.getTradeableOnly()
    val trades: Flow<List<TradeEntity>> = tradeRepository.getAll()

    private val _uiState = MutableStateFlow<TradeUiState>(TradeUiState.Idle)
    val uiState: StateFlow<TradeUiState> = _uiState

    fun createTradeOffer(offeredPokemonId: Int, requestedPokemonId: Int) {
        viewModelScope.launch {
            _uiState.value = TradeUiState.Creating
            try {
                if (!ownedPokemonRepository.owns(offeredPokemonId)) {
                    _uiState.value = TradeUiState.Error("No posees ese Pokémon")
                    return@launch
                }
                if (requestedPokemonId !in 1..151) {
                    _uiState.value = TradeUiState.Error("ID inválido (debe ser entre 1 y 151)")
                    return@launch
                }

                val profile = userRepository.ensureProfileExists()
                val tradeOffer = TradeOffer(
                    tradeId = UUID.randomUUID().toString(),
                    fromUserId = profile.userId,
                    offerPokemonId = offeredPokemonId,
                    requestPokemonId = requestedPokemonId,
                    offerPokemonName = PokemonNames.getName(offeredPokemonId),
                    requestPokemonName = PokemonNames.getName(requestedPokemonId),
                    nonce = UUID.randomUUID().toString().take(8)
                )

                tradeRepository.insert(
                    TradeEntity(
                        tradeId = tradeOffer.tradeId,
                        status = "pending",
                        offeredPokemonId = offeredPokemonId,
                        requestedPokemonId = requestedPokemonId,
                        peerUserId = ""
                    )
                )

                val json = gson.toJson(tradeOffer)
                val barcodeEncoder = BarcodeEncoder()
                val bitmap = barcodeEncoder.encodeBitmap(json, BarcodeFormat.QR_CODE, 512, 512)

                _uiState.value = TradeUiState.QrGenerated(bitmap, tradeOffer)
            } catch (e: Exception) {
                _uiState.value = TradeUiState.Error(e.message ?: "Error al crear oferta")
            }
        }
    }

    fun acceptTrade(tradeOffer: TradeOffer) {
        viewModelScope.launch {
            _uiState.value = TradeUiState.Creating
            try {
                // Validate that B has the requested Pokemon
                if (!ownedPokemonRepository.owns(tradeOffer.requestPokemonId)) {
                    _uiState.value = TradeUiState.Error(
                        "No posees a ${tradeOffer.requestPokemonName.ifEmpty { "#${tradeOffer.requestPokemonId}" }}"
                    )
                    return@launch
                }

                // Check for duplicate trade
                val existingTrade = tradeRepository.getById(tradeOffer.tradeId)
                if (existingTrade != null) {
                    _uiState.value = TradeUiState.Error("Este intercambio ya fue procesado")
                    return@launch
                }

                val profile = userRepository.ensureProfileExists()

                // B receives A's Pokemon (nobody loses anything)
                val offerName = tradeOffer.offerPokemonName.ifEmpty {
                    PokemonNames.getName(tradeOffer.offerPokemonId)
                }
                ownedPokemonRepository.add(
                    pokemonId = tradeOffer.offerPokemonId,
                    nickname = offerName,
                    obtainedVia = "trade",
                    isNewFromTrade = true
                )
                unlockRepository.unlock(tradeOffer.offerPokemonId)

                val ownedCount = ownedPokemonRepository.getAll().first().size
                val unlockedCount = unlockRepository.getAll().first().size
                val points = app.userRepository.getPoints()
                app.firebaseRepository.syncTrainerStats(ownedCount, unlockedCount, points)
                missionRepository.onTradeCompleted(tradeOffer.tradeId, "acceptor")

                // Save trade record on B's side
                tradeRepository.insert(
                    TradeEntity(
                        tradeId = tradeOffer.tradeId,
                        status = "completed",
                        offeredPokemonId = tradeOffer.requestPokemonId,
                        requestedPokemonId = tradeOffer.offerPokemonId,
                        peerUserId = tradeOffer.fromUserId
                    )
                )

                // Generate confirmation QR for A to scan
                val confirmation = tradeOffer.copy(
                    isConfirmation = true,
                    fromUserId = profile.userId
                )
                val json = gson.toJson(confirmation)
                val barcodeEncoder = BarcodeEncoder()
                val bitmap = barcodeEncoder.encodeBitmap(json, BarcodeFormat.QR_CODE, 512, 512)

                _uiState.value = TradeUiState.QrGenerated(bitmap, confirmation)
            } catch (e: Exception) {
                _uiState.value = TradeUiState.Error(e.message ?: "Error al aceptar intercambio")
            }
        }
    }

    fun completeTradeFromConfirmation(tradeOffer: TradeOffer) {
        viewModelScope.launch {
            try {
                val ourTrade = tradeRepository.getById(tradeOffer.tradeId)
                if (ourTrade == null || ourTrade.status != "pending") {
                    _uiState.value = TradeUiState.Error("Intercambio no encontrado o ya completado")
                    return@launch
                }

                // A receives B's Pokemon (nobody loses anything)
                val requestName = PokemonNames.getName(ourTrade.requestedPokemonId)
                ownedPokemonRepository.add(
                    pokemonId = ourTrade.requestedPokemonId,
                    nickname = requestName,
                    obtainedVia = "trade",
                    isNewFromTrade = true
                )
                unlockRepository.unlock(ourTrade.requestedPokemonId)

                val ownedCount = ownedPokemonRepository.getAll().first().size
                val unlockedCount = unlockRepository.getAll().first().size
                val points = app.userRepository.getPoints()
                app.firebaseRepository.syncTrainerStats(ownedCount, unlockedCount, points)
                missionRepository.onTradeCompleted(tradeOffer.tradeId, "creator")

                // Update trade status
                tradeRepository.update(
                    ourTrade.copy(
                        status = "completed",
                        peerUserId = tradeOffer.fromUserId
                    )
                )

                _uiState.value = TradeUiState.TradeSuccess(
                    receivedPokemonId = ourTrade.requestedPokemonId,
                    receivedPokemonName = requestName
                )
            } catch (e: Exception) {
                _uiState.value = TradeUiState.Error(e.message ?: "Error al completar intercambio")
            }
        }
    }

    fun resetState() {
        _uiState.value = TradeUiState.Idle
    }

    fun parseTradeOffer(json: String): TradeOffer? {
        return try {
            gson.fromJson(json, TradeOffer::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
