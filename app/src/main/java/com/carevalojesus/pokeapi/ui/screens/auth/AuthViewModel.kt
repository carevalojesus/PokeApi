package com.carevalojesus.pokeapi.ui.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.firebase.AppUserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object LoggedOut : AuthUiState
    data object Trainer : AuthUiState
    data object Admin : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication
    private val firebaseRepository = app.firebaseRepository

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        refreshSession()
    }

    fun refreshSession() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val role = firebaseRepository.resolveCurrentUserRole()
                _uiState.value = when (role) {
                    AppUserRole.ADMIN -> AuthUiState.Admin
                    AppUserRole.TRAINER -> AuthUiState.Trainer
                    null -> AuthUiState.LoggedOut
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "No se pudo validar sesion")
            }
        }
    }

    fun registerTrainer(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = firebaseRepository.registerTrainer(email, password)
            if (result.isFailure) {
                _uiState.value = AuthUiState.Error(
                    result.exceptionOrNull()?.message ?: "No se pudo registrar entrenador"
                )
                return@launch
            }
            _uiState.value = AuthUiState.Trainer
        }
    }

    fun signInTrainer(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = firebaseRepository.signInTrainer(email, password)
            if (result.isFailure) {
                _uiState.value = AuthUiState.Error(
                    result.exceptionOrNull()?.message ?: "No se pudo iniciar sesion de entrenador"
                )
                return@launch
            }
            _uiState.value = AuthUiState.Trainer
        }
    }

    fun signInAdmin(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = firebaseRepository.signInAdmin(email, password)
            if (result.isFailure) {
                _uiState.value = AuthUiState.Error(result.exceptionOrNull()?.message ?: "No se pudo iniciar sesion")
                return@launch
            }
            _uiState.value = AuthUiState.Admin
        }
    }

    fun signOut() {
        viewModelScope.launch {
            app.clearAllLocalData()
            firebaseRepository.signOut()
            _uiState.value = AuthUiState.LoggedOut
        }
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.LoggedOut
        }
    }
}
