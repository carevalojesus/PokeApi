package com.carevalojesus.pokeapi.ui.screens.admin

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.firebase.CampaignInfo
import com.carevalojesus.pokeapi.data.firebase.CampaignQrCodeData
import com.carevalojesus.pokeapi.data.firebase.TrainerRewardClaim
import com.carevalojesus.pokeapi.data.firebase.TrainerWithInventory
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ResetDbState {
    data object Idle : ResetDbState
    data object Loading : ResetDbState
    data object Success : ResetDbState
    data class Error(val message: String) : ResetDbState
}

sealed interface AdminUiState {
    data object Idle : AdminUiState
    data object Loading : AdminUiState
    data class CampaignCreated(
        val campaignId: String,
        val campaignName: String,
        val qrCodes: List<CampaignQrCodeData>,
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

    private val _resetDbState = MutableStateFlow<ResetDbState>(ResetDbState.Idle)
    val resetDbState: StateFlow<ResetDbState> = _resetDbState

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

    fun createCampaign(name: String, qrCount: Int) {
        viewModelScope.launch {
            _uiState.value = AdminUiState.Loading
            val result = firebaseRepository.createRewardCampaign(name, qrCount = qrCount)
            if (result.isFailure) {
                _uiState.value = AdminUiState.Error(
                    result.exceptionOrNull()?.message ?: "No se pudo crear campaña"
                )
                return@launch
            }

            val data = result.getOrNull() ?: return@launch
            val firstPayload = data.qrCodes.firstOrNull()?.payload
                ?: "pokeapi://reward?campaignId=${data.campaignId}"
            val bitmap = BarcodeEncoder().encodeBitmap(firstPayload, BarcodeFormat.QR_CODE, 520, 520)
            _uiState.value = AdminUiState.CampaignCreated(
                campaignId = data.campaignId,
                campaignName = data.campaignName,
                qrCodes = data.qrCodes,
                bitmap = bitmap
            )
            refreshCampaigns()
        }
    }

    fun showCampaignQr(campaign: CampaignInfo) {
        viewModelScope.launch {
            val qrCodes = firebaseRepository.getCampaignQrCodes(campaign.campaignId)
                .getOrDefault(emptyList())
                .ifEmpty {
                    val fallbackPayload = campaign.qrPayload.ifBlank {
                        "pokeapi://reward?campaignId=${campaign.campaignId}"
                    }
                    listOf(CampaignQrCodeData(codeId = "legacy", payload = fallbackPayload))
                }
            val bitmap = BarcodeEncoder().encodeBitmap(
                qrCodes.first().payload,
                BarcodeFormat.QR_CODE,
                520,
                520
            )
            _uiState.value = AdminUiState.CampaignCreated(
                campaignId = campaign.campaignId,
                campaignName = campaign.name,
                qrCodes = qrCodes,
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

    fun resetDatabase() {
        viewModelScope.launch {
            _resetDbState.value = ResetDbState.Loading
            val result = firebaseRepository.resetDatabaseKeepingAdmin()
            _resetDbState.value = if (result.isSuccess) {
                ResetDbState.Success
            } else {
                ResetDbState.Error(
                    result.exceptionOrNull()?.message ?: "No se pudo resetear la base de datos"
                )
            }
        }
    }

    fun clearResetState() {
        _resetDbState.value = ResetDbState.Idle
    }

    fun clearMessage() {
        if (_uiState.value is AdminUiState.Error) {
            _uiState.value = AdminUiState.Idle
        }
    }
}
