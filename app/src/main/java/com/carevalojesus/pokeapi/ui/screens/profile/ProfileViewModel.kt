package com.carevalojesus.pokeapi.ui.screens.profile

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.local.OwnedPokemonEntity
import com.carevalojesus.pokeapi.data.local.PointEventEntity
import com.carevalojesus.pokeapi.data.local.TradeEntity
import com.carevalojesus.pokeapi.data.local.UnlockedPokemonEntity
import com.carevalojesus.pokeapi.data.local.UserProfileEntity
import com.carevalojesus.pokeapi.data.repository.MarketplaceCatalog
import com.carevalojesus.pokeapi.data.repository.MarketplaceItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication

    val userEmail: String = app.firebaseRepository.getCurrentUserEmail() ?: ""

    private val _passwordChangeState = MutableStateFlow<PasswordChangeState>(PasswordChangeState.Idle)
    val passwordChangeState: StateFlow<PasswordChangeState> = _passwordChangeState

    private val _photoUploadError = MutableStateFlow<String?>(null)
    val photoUploadError: StateFlow<String?> = _photoUploadError

    fun clearPhotoUploadError() { _photoUploadError.value = null }

    val profile: StateFlow<UserProfileEntity?> = app.userRepository.getProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val ownedPokemon: StateFlow<List<OwnedPokemonEntity>> = app.ownedPokemonRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val unlockedPokemon: StateFlow<List<UnlockedPokemonEntity>> = app.unlockRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trades: StateFlow<List<TradeEntity>> = app.tradeRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pointEvents: StateFlow<List<PointEventEntity>> = app.database.pointEventDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val equippedMarketplaceItems: StateFlow<List<MarketplaceItem>> = app.marketplaceRepository
        .getEquippedItemIdsFlow()
        .map { equippedIds ->
            MarketplaceCatalog.items.filter { it.id in equippedIds.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun savePersonalInfo(firstName: String, lastName: String, birthDate: String, gender: String) {
        viewModelScope.launch {
            app.userRepository.updatePersonalInfo(firstName, lastName, birthDate, gender)
            app.firebaseRepository.updateTrainerPersonalInfo(firstName, lastName, birthDate, gender)
        }
    }

    fun saveProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            val context: Context = getApplication()
            val destFile = File(context.filesDir, "profile_photo.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            app.userRepository.updateProfilePhoto(destFile.absolutePath)
            val uid = app.firebaseRepository.getCurrentUserUid()
            if (uid != null) {
                try {
                    val photoBytes = destFile.readBytes()
                    val remoteUrl = app.firebaseRepository.uploadProfilePhoto(uid, photoBytes)
                    app.userRepository.updateProfilePhoto(remoteUrl)
                } catch (e: Exception) {
                    _photoUploadError.value = e.message ?: "Error al subir la foto"
                }
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _passwordChangeState.value = PasswordChangeState.Loading
            val result = app.firebaseRepository.changePassword(currentPassword, newPassword)
            _passwordChangeState.value = if (result.isSuccess) {
                PasswordChangeState.Success
            } else {
                PasswordChangeState.Error(result.exceptionOrNull()?.message ?: "Error al cambiar contraseña")
            }
        }
    }

    fun resetPasswordChangeState() {
        _passwordChangeState.value = PasswordChangeState.Idle
    }
}

sealed interface PasswordChangeState {
    data object Idle : PasswordChangeState
    data object Loading : PasswordChangeState
    data object Success : PasswordChangeState
    data class Error(val message: String) : PasswordChangeState
}
