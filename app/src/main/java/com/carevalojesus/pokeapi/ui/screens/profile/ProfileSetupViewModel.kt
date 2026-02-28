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
        viewModelScope.launch {
            app.userRepository.ensureProfileExists()
            app.userRepository.updatePersonalInfo(firstName, lastName, birthDate, gender)
            if (_photoPath.value.isNotEmpty()) {
                app.userRepository.updateProfilePhoto(_photoPath.value)
            }
            app.firebaseRepository.updateTrainerPersonalInfo(firstName, lastName, birthDate, gender)
            onComplete()
        }
    }
}
