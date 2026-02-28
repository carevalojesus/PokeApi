package com.carevalojesus.pokeapi.ui.screens.trade

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.firebase.TradeFirestoreData
import com.carevalojesus.pokeapi.data.firebase.TradeResult
import com.carevalojesus.pokeapi.data.local.TradeEntity
import com.carevalojesus.pokeapi.data.repository.AggregatedPokemon
import com.carevalojesus.pokeapi.util.PokemonNames
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TradeUiState {
    data object Idle : TradeUiState
    data object Creating : TradeUiState
    data object Loading : TradeUiState
    data class QrGenerated(
        val bitmap: Bitmap,
        val offerPokemonId: Int,
        val offerPokemonName: String,
        val requestPokemonId: Int,
        val requestPokemonName: String
    ) : TradeUiState
    data class TradeLoaded(
        val trade: TradeFirestoreData,
        val currentUserUid: String
    ) : TradeUiState
    data class AcceptedShowQr(
        val bitmap: Bitmap,
        val trade: TradeFirestoreData
    ) : TradeUiState
    data class TradeSuccess(
        val receivedPokemonId: Int,
        val receivedPokemonName: String
    ) : TradeUiState
    data class Error(val message: String) : TradeUiState
}

class TradeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication
    private val ownedPokemonRepository = app.ownedPokemonRepository
    private val unlockRepository = app.unlockRepository
    private val tradeRepository = app.tradeRepository
    private val missionRepository = app.missionRepository
    private val firebaseRepository = app.firebaseRepository

    val tradeablePokemon: StateFlow<List<AggregatedPokemon>> = ownedPokemonRepository.getAll()
        .map { list ->
            list.groupBy { it.pokemonId }
                .filter { (_, entities) -> entities.size >= 2 }
                .map { (pokemonId, entities) ->
                    AggregatedPokemon(
                        pokemonId = pokemonId,
                        count = entities.size,
                        isStarter = entities.any { it.isStarter },
                        nickname = entities.first().nickname,
                        hasNewFromTrade = false,
                        representativeId = entities.first().id
                    )
                }
                .sortedBy { it.pokemonId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trades: StateFlow<List<TradeEntity>> = tradeRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                val count = ownedPokemonRepository.countByPokemonId(offeredPokemonId)
                if (count < 2) {
                    _uiState.value = TradeUiState.Error("Solo tienes una copia, no puedes intercambiarlo")
                    return@launch
                }
                if (requestedPokemonId !in 1..151) {
                    _uiState.value = TradeUiState.Error("ID inválido (debe ser entre 1 y 151)")
                    return@launch
                }

                val offerName = PokemonNames.getName(offeredPokemonId)
                val requestName = PokemonNames.getName(requestedPokemonId)

                val result = firebaseRepository.createTrade(
                    offerPokemonId = offeredPokemonId,
                    requestPokemonId = requestedPokemonId,
                    offerPokemonName = offerName,
                    requestPokemonName = requestName
                )

                when (result) {
                    is TradeResult.Success -> {
                        tradeRepository.insert(
                            TradeEntity(
                                tradeId = result.tradeId,
                                status = "pending",
                                offeredPokemonId = offeredPokemonId,
                                requestedPokemonId = requestedPokemonId,
                                peerUserId = ""
                            )
                        )

                        val qrContent = "pokeapi://trade/${result.tradeId}"
                        val barcodeEncoder = BarcodeEncoder()
                        val bitmap = barcodeEncoder.encodeBitmap(qrContent, BarcodeFormat.QR_CODE, 512, 512)

                        _uiState.value = TradeUiState.QrGenerated(
                            bitmap = bitmap,
                            offerPokemonId = offeredPokemonId,
                            offerPokemonName = offerName,
                            requestPokemonId = requestedPokemonId,
                            requestPokemonName = requestName
                        )
                    }
                    is TradeResult.Error -> {
                        _uiState.value = TradeUiState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = TradeUiState.Error(e.message ?: "Error al crear oferta")
            }
        }
    }

    fun fetchTradeFromFirestore(tradeId: String) {
        viewModelScope.launch {
            _uiState.value = TradeUiState.Loading
            try {
                val trade = firebaseRepository.fetchTrade(tradeId)
                if (trade == null) {
                    _uiState.value = TradeUiState.Error("Intercambio no encontrado")
                    return@launch
                }
                val currentUid = firebaseRepository.getCurrentUserUid() ?: ""
                _uiState.value = TradeUiState.TradeLoaded(trade, currentUid)
            } catch (e: Exception) {
                _uiState.value = TradeUiState.Error(e.message ?: "Error al cargar intercambio")
            }
        }
    }

    fun acceptTrade(tradeId: String) {
        viewModelScope.launch {
            _uiState.value = TradeUiState.Creating
            try {
                val result = firebaseRepository.acceptTrade(tradeId)
                when (result) {
                    is TradeResult.Success -> {
                        // Fetch the trade to get pokemon details
                        val trade = firebaseRepository.fetchTrade(tradeId)
                        if (trade != null) {
                            // Update Room local: B loses requested, gains offered
                            ownedPokemonRepository.removeOneByPokemonId(trade.requestPokemonId)
                            ownedPokemonRepository.add(
                                pokemonId = trade.offerPokemonId,
                                nickname = trade.offerPokemonName,
                                obtainedVia = "trade",
                                isNewFromTrade = true
                            )
                            unlockRepository.unlock(trade.offerPokemonId)

                            val ownedCount = ownedPokemonRepository.getAll().first().size
                            val unlockedCount = unlockRepository.getAll().first().size
                            val points = app.userRepository.getPoints()
                            firebaseRepository.syncTrainerStats(ownedCount, unlockedCount, points)
                            missionRepository.onTradeCompleted(tradeId, "acceptor")

                            // Save trade record on B's side
                            tradeRepository.insert(
                                TradeEntity(
                                    tradeId = tradeId,
                                    status = "completed",
                                    offeredPokemonId = trade.requestPokemonId,
                                    requestedPokemonId = trade.offerPokemonId,
                                    peerUserId = trade.creatorUid
                                )
                            )

                            // Generate QR with same tradeId for A to scan
                            val qrContent = "pokeapi://trade/$tradeId"
                            val barcodeEncoder = BarcodeEncoder()
                            val bitmap = barcodeEncoder.encodeBitmap(qrContent, BarcodeFormat.QR_CODE, 512, 512)

                            _uiState.value = TradeUiState.AcceptedShowQr(bitmap, trade)
                        } else {
                            _uiState.value = TradeUiState.Error("No se pudo obtener datos del intercambio")
                        }
                    }
                    is TradeResult.Error -> {
                        _uiState.value = TradeUiState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = TradeUiState.Error(e.message ?: "Error al aceptar intercambio")
            }
        }
    }

    fun completeTrade(tradeId: String) {
        viewModelScope.launch {
            _uiState.value = TradeUiState.Creating
            try {
                // Fetch trade to get pokemon details before completing
                val trade = firebaseRepository.fetchTrade(tradeId)
                if (trade == null) {
                    _uiState.value = TradeUiState.Error("Intercambio no encontrado")
                    return@launch
                }
                if (trade.status != "accepted") {
                    _uiState.value = TradeUiState.Error("Intercambio no está listo para completar")
                    return@launch
                }

                val result = firebaseRepository.completeTrade(tradeId)
                when (result) {
                    is TradeResult.Success -> {
                        // Update Room local: A loses offered, gains requested
                        ownedPokemonRepository.removeOneByPokemonId(trade.offerPokemonId)
                        ownedPokemonRepository.add(
                            pokemonId = trade.requestPokemonId,
                            nickname = trade.requestPokemonName,
                            obtainedVia = "trade",
                            isNewFromTrade = true
                        )
                        unlockRepository.unlock(trade.requestPokemonId)

                        val ownedCount = ownedPokemonRepository.getAll().first().size
                        val unlockedCount = unlockRepository.getAll().first().size
                        val points = app.userRepository.getPoints()
                        firebaseRepository.syncTrainerStats(ownedCount, unlockedCount, points)
                        missionRepository.onTradeCompleted(tradeId, "creator")

                        // Update trade status in Room
                        val existingTrade = tradeRepository.getById(tradeId)
                        if (existingTrade != null) {
                            tradeRepository.update(
                                existingTrade.copy(
                                    status = "completed",
                                    peerUserId = trade.acceptorUid
                                )
                            )
                        }

                        _uiState.value = TradeUiState.TradeSuccess(
                            receivedPokemonId = trade.requestPokemonId,
                            receivedPokemonName = trade.requestPokemonName
                        )
                    }
                    is TradeResult.Error -> {
                        _uiState.value = TradeUiState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = TradeUiState.Error(e.message ?: "Error al completar intercambio")
            }
        }
    }

    fun parseTradeId(scannedContent: String): String? {
        val prefix = "pokeapi://trade/"
        val trimmed = scannedContent.trim()
        if (!trimmed.startsWith(prefix)) return null
        val tradeId = trimmed.removePrefix(prefix).trim()
        if (tradeId.isBlank() || tradeId.length > 120) return null
        return tradeId
    }

    fun resetState() {
        _uiState.value = TradeUiState.Idle
    }
}
