package com.example.medicaseclinico.ui.screens.auth.login

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
 * Estado del formulario de inicio de sesión.
 *
 * No sigue el patrón Loading/Empty/Success/Error de las pantallas de datos:
 * un formulario está siempre a la vista y lo que cambia son sus errores y si
 * se está enviando.
 *
 * @param error fallo de la última llamada al servidor (credenciales, red…).
 * @param isSignedIn true cuando la sesión quedó iniciada: la pantalla debe salir.
 */
@Immutable
data class LoginUiState(
    val emailError: AuthFieldError? = null,
    val passwordError: AuthFieldError? = null,
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
    val isSignedIn: Boolean = false
)

/**
 * Valida el formulario de inicio de sesión y llama al repositorio de cuentas.
 */
class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    // Los textos son estado de Compose y no parte del StateFlow: un campo
    // alimentado por un flujo se actualiza con retraso y pierde letras.
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** Al corregir un campo se quita su error: ya no describe lo que hay escrito. */
    fun onEmailChange(value: String) {
        email = value
        _uiState.update { it.copy(emailError = null, error = null) }
    }

    fun onPasswordChange(value: String) {
        password = value
        _uiState.update { it.copy(passwordError = null, error = null) }
    }

    fun signIn() {
        if (_uiState.value.isSubmitting) return

        val emailError = when {
            email.isBlank() -> AuthFieldError.EMAIL_REQUIRED
            !AuthValidation.isValidEmail(email) -> AuthFieldError.EMAIL_INVALID
            else -> null
        }
        // Aquí no se exige longitud mínima: eso es para contraseñas nuevas, y
        // rechazarla revelaría la regla a quien prueba contraseñas ajenas.
        val passwordError = if (password.isEmpty()) AuthFieldError.PASSWORD_REQUIRED else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError, error = null) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            authRepository.signIn(email = email, password = password)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, isSignedIn = true) }
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                LoginViewModel(authRepository = application.container.authRepository)
            }
        }
    }
}
