package com.carevalojesus.pokeapi.ui.screens.trade

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.local.OwnedPokemonEntity
import com.carevalojesus.pokeapi.data.local.TradeEntity
import com.carevalojesus.pokeapi.domain.model.TradeOffer
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface TradeUiState {
    data object Idle : TradeUiState
    data object Creating : TradeUiState
    data class QrGenerated(val bitmap: Bitmap, val tradeOffer: TradeOffer) : TradeUiState
    data class Error(val message: String) : TradeUiState
}

class TradeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication
    private val userRepository = app.userRepository
    private val ownedPokemonRepository = app.ownedPokemonRepository
    private val tradeRepository = app.tradeRepository

    private val gson = Gson()

    val tradeablePokemon: Flow<List<OwnedPokemonEntity>> = ownedPokemonRepository.getTradeableOnly()
    val trades: Flow<List<TradeEntity>> = tradeRepository.getAll()

    private val _uiState = MutableStateFlow<TradeUiState>(TradeUiState.Idle)
    val uiState: StateFlow<TradeUiState> = _uiState

    fun createTradeOffer(offeredPokemonId: Int, requestedPokemonId: Int) {
        viewModelScope.launch {
            _uiState.value = TradeUiState.Creating
            try {
                // Validate ownership
                if (!ownedPokemonRepository.owns(offeredPokemonId)) {
                    _uiState.value = TradeUiState.Error("No posees ese Pokemon")
                    return@launch
                }

                val profile = userRepository.ensureProfileExists()
                val tradeOffer = TradeOffer(
                    tradeId = UUID.randomUUID().toString(),
                    fromUserId = profile.userId,
                    offerPokemonId = offeredPokemonId,
                    requestPokemonId = requestedPokemonId,
                    nonce = UUID.randomUUID().toString().take(8)
                )

                // Save trade record
                tradeRepository.insert(
                    TradeEntity(
                        tradeId = tradeOffer.tradeId,
                        status = "pending",
                        offeredPokemonId = offeredPokemonId,
                        requestedPokemonId = requestedPokemonId,
                        peerUserId = ""
                    )
                )

                // Generate QR
                val json = gson.toJson(tradeOffer)
                val barcodeEncoder = BarcodeEncoder()
                val bitmap = barcodeEncoder.encodeBitmap(json, BarcodeFormat.QR_CODE, 512, 512)

                _uiState.value = TradeUiState.QrGenerated(bitmap, tradeOffer)
            } catch (e: Exception) {
                _uiState.value = TradeUiState.Error(e.message ?: "Error al crear oferta")
            }
        }
    }

    fun acceptTrade(tradeOffer: TradeOffer): Bitmap? {
        var resultBitmap: Bitmap? = null
        viewModelScope.launch {
            try {
                // Validate that B has the requested Pokemon
                if (!ownedPokemonRepository.owns(tradeOffer.requestPokemonId)) {
                    _uiState.value = TradeUiState.Error("No posees el Pokemon solicitado")
                    return@launch
                }

                // Check for duplicate trade
                val existingTrade = tradeRepository.getById(tradeOffer.tradeId)
                if (existingTrade != null) {
                    _uiState.value = TradeUiState.Error("Este intercambio ya fue procesado")
                    return@launch
                }

                val profile = userRepository.ensureProfileExists()

                // Execute trade on B's side: remove requested, add offered
                ownedPokemonRepository.removeOneByPokemonId(tradeOffer.requestPokemonId)
                ownedPokemonRepository.add(tradeOffer.offerPokemonId)

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

                // Generate confirmation QR
                val confirmation = tradeOffer.copy(
                    isConfirmation = true,
                    fromUserId = profile.userId
                )
                val json = gson.toJson(confirmation)
                val barcodeEncoder = BarcodeEncoder()
                resultBitmap = barcodeEncoder.encodeBitmap(json, BarcodeFormat.QR_CODE, 512, 512)

                _uiState.value = TradeUiState.QrGenerated(resultBitmap!!, confirmation)
            } catch (e: Exception) {
                _uiState.value = TradeUiState.Error(e.message ?: "Error al aceptar intercambio")
            }
        }
        return resultBitmap
    }

    fun completeTradeFromConfirmation(tradeOffer: TradeOffer) {
        viewModelScope.launch {
            try {
                // Find our pending trade
                val ourTrade = tradeRepository.getById(tradeOffer.tradeId)
                if (ourTrade == null || ourTrade.status != "pending") {
                    _uiState.value = TradeUiState.Error("Intercambio no encontrado o ya completado")
                    return@launch
                }

                // Execute trade on A's side: remove offered, add requested
                ownedPokemonRepository.removeOneByPokemonId(ourTrade.offeredPokemonId)
                ownedPokemonRepository.add(ourTrade.requestedPokemonId)

                // Update trade status
                tradeRepository.update(
                    ourTrade.copy(
                        status = "completed",
                        peerUserId = tradeOffer.fromUserId
                    )
                )

                _uiState.value = TradeUiState.Idle
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
