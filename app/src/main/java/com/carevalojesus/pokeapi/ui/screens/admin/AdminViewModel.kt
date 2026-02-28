package com.carevalojesus.pokeapi.ui.screens.admin

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.firebase.CampaignInfo
import com.carevalojesus.pokeapi.data.firebase.TrainerRewardClaim
import com.carevalojesus.pokeapi.data.firebase.TrainerWithInventory
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AdminUiState {
    data object Idle : AdminUiState
    data object Loading : AdminUiState
    data class CampaignCreated(
        val campaignId: String,
        val campaignName: String,
        val payload: String,
        val bitmap: Bitmap,
        val isNewlyCreated: Boolean = true
    ) : AdminUiState
    data class Error(val message: String) : AdminUiState
}

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication
    private val firebaseRepository = app.firebaseRepository

    private val _uiState = MutableStateFlow<AdminUiState>(AdminUiState.Idle)
    val uiState: StateFlow<AdminUiState> = _uiState

    val trainers: StateFlow<List<TrainerWithInventory>> = firebaseRepository.observeTrainersWithInventory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _campaigns = MutableStateFlow<List<CampaignInfo>>(emptyList())
    val campaigns: StateFlow<List<CampaignInfo>> = _campaigns

    private val _broadcastStatus = MutableStateFlow<String?>(null)
    val broadcastStatus: StateFlow<String?> = _broadcastStatus

    private val _trainerRewards = MutableStateFlow<List<TrainerRewardClaim>>(emptyList())
    val trainerRewards: StateFlow<List<TrainerRewardClaim>> = _trainerRewards

    private val _trainerHistoryLoading = MutableStateFlow(false)
    val trainerHistoryLoading: StateFlow<Boolean> = _trainerHistoryLoading

    fun loadTrainerHistory(trainerUid: String) {
        viewModelScope.launch {
            _trainerHistoryLoading.value = true
            _trainerRewards.value = emptyList()
            val result = firebaseRepository.getTrainerRewardHistory(trainerUid)
            if (result.isSuccess) {
                _trainerRewards.value = result.getOrDefault(emptyList())
            }
            _trainerHistoryLoading.value = false
        }
    }

    fun refreshTrainers() {
        // Los entrenadores se actualizan en tiempo real via snapshot listener.
        // Este método se mantiene por compatibilidad con la UI.
    }

    fun refreshCampaigns() {
        viewModelScope.launch {
            val result = firebaseRepository.getAllCampaigns()
            if (result.isSuccess) {
                _campaigns.value = result.getOrDefault(emptyList())
            } else {
                _uiState.value = AdminUiState.Error(
                    result.exceptionOrNull()?.message ?: "No se pudo cargar campañas"
                )
            }
        }
    }

    fun toggleCampaign(campaignId: String, active: Boolean) {
        viewModelScope.launch {
            val result = firebaseRepository.toggleCampaignActive(campaignId, active)
            if (result.isFailure) {
                _uiState.value = AdminUiState.Error(
                    result.exceptionOrNull()?.message ?: "No se pudo actualizar campaña"
                )
            }
            refreshCampaigns()
        }
    }

    fun deleteCampaign(campaignId: String) {
        viewModelScope.launch {
            val result = firebaseRepository.deleteCampaign(campaignId)
            if (result.isFailure) {
                _uiState.value = AdminUiState.Error(
                    result.exceptionOrNull()?.message ?: "No se pudo eliminar campaña"
                )
            }
            refreshCampaigns()
        }
    }

    fun createCampaign(name: String) {
        viewModelScope.launch {
            _uiState.value = AdminUiState.Loading
            val result = firebaseRepository.createRewardCampaign(name)
            if (result.isFailure) {
                _uiState.value = AdminUiState.Error(
                    result.exceptionOrNull()?.message ?: "No se pudo crear campaña"
                )
                return@launch
            }

            val data = result.getOrNull() ?: return@launch
            val bitmap = BarcodeEncoder().encodeBitmap(data.qrPayload, BarcodeFormat.QR_CODE, 520, 520)
            _uiState.value = AdminUiState.CampaignCreated(
                campaignId = data.campaignId,
                campaignName = data.campaignName,
                payload = data.qrPayload,
                bitmap = bitmap
            )
            refreshCampaigns()
        }
    }

    fun showCampaignQr(campaign: CampaignInfo) {
        viewModelScope.launch {
            val payload = campaign.qrPayload.ifBlank {
                "pokeapi://reward?campaignId=${campaign.campaignId}"
            }
            val bitmap = BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, 520, 520)
            _uiState.value = AdminUiState.CampaignCreated(
                campaignId = campaign.campaignId,
                campaignName = campaign.name,
                payload = payload,
                bitmap = bitmap,
                isNewlyCreated = false
            )
        }
    }

    fun sendNotificationToTrainers(title: String, message: String) {
        viewModelScope.launch {
            val result = firebaseRepository.sendAdminBroadcastNotification(title, message)
            if (result.isSuccess) {
                val recipients = result.getOrDefault(0)
                _broadcastStatus.value = "Notificación enviada a $recipients entrenadores."
            } else {
                _broadcastStatus.value = result.exceptionOrNull()?.message ?: "No se pudo enviar la notificación"
            }
        }
    }

    fun clearBroadcastStatus() {
        _broadcastStatus.value = null
    }

    fun resetUiState() {
        _uiState.value = AdminUiState.Idle
    }

    fun clearMessage() {
        if (_uiState.value is AdminUiState.Error) {
            _uiState.value = AdminUiState.Idle
        }
    }
}
