package com.carevalojesus.pokeapi.ui.screens.profile

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class ProfileSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _photoPath = MutableStateFlow("")
    val photoPath: StateFlow<String> = _photoPath

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun clearError() { _errorMessage.value = null }

    fun pickPhoto(uri: Uri) {
        viewModelScope.launch {
            val context: Context = getApplication()
            val destFile = File(context.filesDir, "profile_photo.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            _photoPath.value = destFile.absolutePath
        }
    }

    fun saveProfile(
        firstName: String,
        lastName: String,
        birthDate: String,
        gender: String,
        onComplete: () -> Unit
    ) {
        if (_isSaving.value) return
        _isSaving.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                app.userRepository.ensureProfileExists()
                app.userRepository.updatePersonalInfo(firstName, lastName, birthDate, gender)
                if (_photoPath.value.isNotEmpty()) {
                    app.userRepository.updateProfilePhoto(_photoPath.value)
                    val uid = app.firebaseRepository.getCurrentUserUid()
                    if (uid != null) {
                        val photoBytes = File(_photoPath.value).readBytes()
                        val remoteUrl = app.firebaseRepository.uploadProfilePhoto(uid, photoBytes)
                        app.userRepository.updateProfilePhoto(remoteUrl)
                    }
                }
                app.firebaseRepository.updateTrainerPersonalInfo(firstName, lastName, birthDate, gender)
                app.missionRepository.onProfileCompleted()
                onComplete()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Error al guardar el perfil"
                _isSaving.value = false
            }
        }
    }
}
