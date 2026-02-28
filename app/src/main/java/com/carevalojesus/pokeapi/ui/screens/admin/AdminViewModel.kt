package com.carevalojesus.pokeapi.ui.screens.admin

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.firebase.CampaignInfo
import com.carevalojesus.pokeapi.data.firebase.TrainerWithInventory
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AdminUiState {
    data object Idle : AdminUiState
    data object Loading : AdminUiState
    data class CampaignCreated(
        val campaignId: String,
        val campaignName: String,
        val payload: String,
        val bitmap: Bitmap
    ) : AdminUiState
    data class Error(val message: String) : AdminUiState
}

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication
    private val firebaseRepository = app.firebaseRepository

    private val _uiState = MutableStateFlow<AdminUiState>(AdminUiState.Idle)
    val uiState: StateFlow<AdminUiState> = _uiState

    private val _trainers = MutableStateFlow<List<TrainerWithInventory>>(emptyList())
    val trainers: StateFlow<List<TrainerWithInventory>> = _trainers

    private val _campaigns = MutableStateFlow<List<CampaignInfo>>(emptyList())
    val campaigns: StateFlow<List<CampaignInfo>> = _campaigns

    private val _broadcastStatus = MutableStateFlow<String?>(null)
    val broadcastStatus: StateFlow<String?> = _broadcastStatus

    fun refreshTrainers() {
        viewModelScope.launch {
            val result = firebaseRepository.getTrainersWithInventory()
            if (result.isSuccess) {
                _trainers.value = result.getOrDefault(emptyList())
            } else {
                _uiState.value = AdminUiState.Error(
                    result.exceptionOrNull()?.message ?: "No se pudo cargar entrenadores"
                )
            }
        }
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
                bitmap = bitmap
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

    fun clearMessage() {
        if (_uiState.value is AdminUiState.Error) {
            _uiState.value = AdminUiState.Idle
        }
    }
}
