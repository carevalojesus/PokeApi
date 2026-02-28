package com.carevalojesus.pokeapi.ui.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.firebase.AppUserRole
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import android.util.Patterns
import com.carevalojesus.pokeapi.ui.notifications.SystemNotificationsBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    private val missionRepository = app.missionRepository

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    private val _resetEmailSent = MutableStateFlow(false)
    val resetEmailSent: StateFlow<Boolean> = _resetEmailSent

    init {
        refreshSession()
    }

    fun refreshSession() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val role = withTimeoutOrNull(15_000L) {
                    firebaseRepository.resolveCurrentUserRole()
                }
                if (role == null && firebaseRepository.getCurrentUserUid() != null) {
                    _uiState.value = AuthUiState.LoggedOut
                    _authError.value = "Tiempo de espera agotado. Verifica tu conexión"
                    return@launch
                }
                _uiState.value = when (role) {
                    AppUserRole.ADMIN -> AuthUiState.Admin
                    AppUserRole.TRAINER -> {
                        runCatching { app.syncProgressFromFirebase() }
                        AuthUiState.Trainer
                    }
                    null -> AuthUiState.LoggedOut
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.LoggedOut
            }
        }
    }

    fun registerTrainer(email: String, password: String) {
        if (_isAuthenticating.value) return
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            val normalized = email.trim().lowercase()
            if (!isValidEmail(normalized)) {
                _authError.value = "Ingresa un correo electrónico válido"
                _isAuthenticating.value = false
                return@launch
            }
            if (!normalized.endsWith("@senati.pe")) {
                _authError.value = "Para registrarte como entrenador debes usar un correo @senati.pe"
                _isAuthenticating.value = false
                return@launch
            }
            if (password.length < 6) {
                _authError.value = "La contraseña debe tener al menos 6 caracteres"
                _isAuthenticating.value = false
                return@launch
            }
            val result = withTimeoutOrNull(30_000L) {
                firebaseRepository.registerTrainer(normalized, password)
            }
            if (result == null) {
                _authError.value = "Tiempo de espera agotado. Verifica tu conexión"
                _isAuthenticating.value = false
                return@launch
            }
            if (result.isFailure) {
                _authError.value = mapAuthError(result.exceptionOrNull())
                _isAuthenticating.value = false
                return@launch
            }
            _isAuthenticating.value = false
            missionRepository.onDailyLogin()
            runCatching { app.syncProgressFromFirebase() }
            _uiState.value = AuthUiState.Trainer
        }
    }

    fun signInTrainer(email: String, password: String) {
        if (_isAuthenticating.value) return
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            val normalized = email.trim().lowercase()
            if (!isValidEmail(normalized)) {
                _authError.value = "Ingresa un correo electrónico válido"
                _isAuthenticating.value = false
                return@launch
            }
            if (password.isBlank()) {
                _authError.value = "Ingresa tu contraseña"
                _isAuthenticating.value = false
                return@launch
            }
            val result = withTimeoutOrNull(30_000L) {
                firebaseRepository.signInTrainer(normalized, password)
            }
            if (result == null) {
                _authError.value = "Tiempo de espera agotado. Verifica tu conexión"
                _isAuthenticating.value = false
                return@launch
            }
            if (result.isFailure) {
                _authError.value = mapAuthError(result.exceptionOrNull())
                _isAuthenticating.value = false
                return@launch
            }
            _isAuthenticating.value = false
            missionRepository.onDailyLogin()
            runCatching { app.syncProgressFromFirebase() }
            _uiState.value = AuthUiState.Trainer
        }
    }

    fun signInAdmin(email: String, password: String) {
        if (_isAuthenticating.value) return
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            val normalized = email.trim().lowercase()
            if (!isValidEmail(normalized)) {
                _authError.value = "Ingresa un correo electrónico válido"
                _isAuthenticating.value = false
                return@launch
            }
            if (password.isBlank()) {
                _authError.value = "Ingresa tu contraseña"
                _isAuthenticating.value = false
                return@launch
            }
            val result = withTimeoutOrNull(30_000L) {
                firebaseRepository.signInAdmin(normalized, password)
            }
            if (result == null) {
                _authError.value = "Tiempo de espera agotado. Verifica tu conexión"
                _isAuthenticating.value = false
                return@launch
            }
            if (result.isFailure) {
                _authError.value = mapAuthError(result.exceptionOrNull())
                _isAuthenticating.value = false
                return@launch
            }
            _isAuthenticating.value = false
            _uiState.value = AuthUiState.Admin
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                SystemNotificationsBridge.stop()
                SystemNotificationsBridge.clearShownIds(app)
                app.clearAllLocalData()
            } finally {
                firebaseRepository.signOut()
                _authError.value = null
                _uiState.value = AuthUiState.LoggedOut
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (_isAuthenticating.value) return
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            val normalized = email.trim().lowercase()
            if (!isValidEmail(normalized)) {
                _authError.value = "Ingresa un correo electronico valido"
                _isAuthenticating.value = false
                return@launch
            }
            val result = withTimeoutOrNull(30_000L) {
                firebaseRepository.sendPasswordResetEmail(normalized)
            }
            if (result == null) {
                _authError.value = "Tiempo de espera agotado. Verifica tu conexión"
                _isAuthenticating.value = false
                return@launch
            }
            if (result.isFailure) {
                _authError.value = result.exceptionOrNull()?.message ?: "Error al enviar correo"
            } else {
                _resetEmailSent.value = true
            }
            _isAuthenticating.value = false
        }
    }

    fun clearResetEmailSent() {
        _resetEmailSent.value = false
    }

    fun clearError() {
        _authError.value = null
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun mapAuthError(exception: Throwable?): String {
        if (exception == null) return "Ocurrió un error inesperado"

        return when (exception) {
            is FirebaseAuthInvalidCredentialsException ->
                "Correo o contraseña incorrectos"
            is FirebaseAuthInvalidUserException ->
                "No existe una cuenta con este correo"
            is FirebaseAuthUserCollisionException ->
                "Ya existe una cuenta con este correo"
            is FirebaseAuthWeakPasswordException ->
                "La contraseña debe tener al menos 6 caracteres"
            else -> {
                val msg = exception.message ?: ""
                when {
                    msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                    msg.contains("INVALID_EMAIL", ignoreCase = true) ->
                        "Correo o contraseña incorrectos"
                    msg.contains("USER_NOT_FOUND", ignoreCase = true) ->
                        "No existe una cuenta con este correo"
                    msg.contains("EMAIL_EXISTS", ignoreCase = true) ||
                    msg.contains("already in use", ignoreCase = true) ->
                        "Ya existe una cuenta con este correo"
                    msg.contains("WEAK_PASSWORD", ignoreCase = true) ->
                        "La contraseña debe tener al menos 6 caracteres"
                    msg.contains("TOO_MANY_REQUESTS", ignoreCase = true) ||
                    msg.contains("BLOCKED", ignoreCase = true) ->
                        "Demasiados intentos. Intenta más tarde"
                    msg.contains("NETWORK", ignoreCase = true) ->
                        "Error de conexión. Verifica tu internet"
                    msg.contains("@senati.pe", ignoreCase = true) ->
                        "Para registrarte como entrenador debes usar un correo @senati.pe"
                    msg.contains("admin", ignoreCase = true) ->
                        msg
                    else -> msg.ifBlank { "Ocurrió un error inesperado" }
                }
            }
        }
    }
}
