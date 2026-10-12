package com.example.medicaseclinico.ui.screens.auth.recovery

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.auth.AuthValidation
import com.example.medicaseclinico.domain.model.AuthError
import com.example.medicaseclinico.domain.model.AuthException
import com.example.medicaseclinico.domain.repository.AuthRepository
import com.example.medicaseclinico.ui.screens.auth.AuthFieldError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado del formulario de recuperación de contraseña.
 *
 * @param error fallo de la última llamada al servidor (red, demasiados intentos…).
 * @param sentTo correo al que se pidió enviar el enlace, o null si aún no se
 * ha enviado. Con valor, la pantalla cambia el formulario por la confirmación.
 */
@Immutable
data class PasswordRecoveryUiState(
    val emailError: AuthFieldError? = null,
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
    val sentTo: String? = null
)

/**
 * Valida el correo y pide al repositorio de cuentas el enlace para elegir una
 * contraseña nueva.
 */
class PasswordRecoveryViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    // Estado de Compose y no parte del StateFlow, para que el campo no pierda
    // letras al escribir rápido.
    var email by mutableStateOf("")
        private set

    private val _uiState = MutableStateFlow(PasswordRecoveryUiState())
    val uiState: StateFlow<PasswordRecoveryUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        email = value
        _uiState.update { it.copy(emailError = null, error = null) }
    }

    fun sendResetLink() {
        if (_uiState.value.isSubmitting) return

        val emailError = when {
            email.isBlank() -> AuthFieldError.EMAIL_REQUIRED
            !AuthValidation.isValidEmail(email) -> AuthFieldError.EMAIL_INVALID
            else -> null
        }
        if (emailError != null) {
            _uiState.update { it.copy(emailError = emailError, error = null) }
            return
        }

        val target = email.trim()
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            authRepository.sendPasswordReset(target)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, sentTo = target) }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = (failure as? AuthException)?.error ?: AuthError.UNKNOWN
                        )
                    }
                }
        }
    }

    /** El usuario quiere probar con otro correo: se vuelve al formulario. */
    fun onUseAnotherEmail() {
        _uiState.update { it.copy(sentTo = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                PasswordRecoveryViewModel(authRepository = application.container.authRepository)
            }
        }
    }
}
