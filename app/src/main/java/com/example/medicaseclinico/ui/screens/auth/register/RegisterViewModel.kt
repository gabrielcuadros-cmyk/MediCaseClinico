package com.example.medicaseclinico.ui.screens.auth.register

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
import com.example.medicaseclinico.domain.usecase.RegisterUserUseCase
import com.example.medicaseclinico.ui.screens.auth.AuthFieldError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado del formulario "Crear Cuenta". Como en Iniciar sesión, es un
 * formulario siempre visible: lo que cambia son sus errores y si se está enviando.
 *
 * @param error fallo de la última llamada al servidor (correo ya en uso, red…).
 * @param isRegistered true cuando la cuenta quedó creada y con la sesión
 * iniciada: la pantalla debe salir.
 */
@Immutable
data class RegisterUiState(
    val nameError: AuthFieldError? = null,
    val emailError: AuthFieldError? = null,
    val phoneError: AuthFieldError? = null,
    val passwordError: AuthFieldError? = null,
    val confirmPasswordError: AuthFieldError? = null,
    val termsAccepted: Boolean = false,
    val termsError: AuthFieldError? = null,
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
    val isRegistered: Boolean = false
) {
    val hasFieldErrors: Boolean
        get() = listOf(nameError, emailError, phoneError, passwordError, confirmPasswordError, termsError)
            .any { it != null }
}

/**
 * Valida el formulario de registro y crea la cuenta con su perfil.
 */
class RegisterViewModel(
    private val registerUser: RegisterUserUseCase
) : ViewModel() {

    // Los textos son estado de Compose y no parte del StateFlow, para que los
    // campos no pierdan letras al escribir rápido.
    var name by mutableStateOf("")
        private set
    var email by mutableStateOf("")
        private set
    var phone by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var confirmPassword by mutableStateOf("")
        private set

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    /** Al corregir un campo se quita su error: ya no describe lo que hay escrito. */
    fun onNameChange(value: String) {
        name = value
        _uiState.update { it.copy(nameError = null, error = null) }
    }

    fun onEmailChange(value: String) {
        email = value
        _uiState.update { it.copy(emailError = null, error = null) }
    }

    fun onPhoneChange(value: String) {
        phone = value
        _uiState.update { it.copy(phoneError = null, error = null) }
    }

    /** Cambiar la contraseña también invalida el aviso de "no coinciden". */
    fun onPasswordChange(value: String) {
        password = value
        _uiState.update { it.copy(passwordError = null, confirmPasswordError = null, error = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        confirmPassword = value
        _uiState.update { it.copy(confirmPasswordError = null, error = null) }
    }

    fun onTermsAcceptedChange(accepted: Boolean) {
        _uiState.update { it.copy(termsAccepted = accepted, termsError = null) }
    }

    fun register() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val validated = current.copy(
            nameError = if (AuthValidation.isValidName(name)) null else AuthFieldError.NAME_REQUIRED,
            emailError = when {
                email.isBlank() -> AuthFieldError.EMAIL_REQUIRED
                !AuthValidation.isValidEmail(email) -> AuthFieldError.EMAIL_INVALID
                else -> null
            },
            phoneError = when {
                phone.isBlank() -> AuthFieldError.PHONE_REQUIRED
                !AuthValidation.isValidPhone(phone) -> AuthFieldError.PHONE_INVALID
                else -> null
            },
            passwordError = if (AuthValidation.isValidNewPassword(password)) {
                null
            } else {
                AuthFieldError.PASSWORD_TOO_SHORT
            },
            confirmPasswordError = if (confirmPassword == password) {
                null
            } else {
                AuthFieldError.PASSWORDS_DO_NOT_MATCH
            },
            termsError = if (current.termsAccepted) null else AuthFieldError.TERMS_REQUIRED,
            error = null
        )
        if (validated.hasFieldErrors) {
            _uiState.value = validated
            return
        }

        _uiState.value = validated.copy(isSubmitting = true)
        viewModelScope.launch {
            registerUser(name = name, email = email, phone = phone, password = password)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, isRegistered = true) }
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
                RegisterViewModel(
                    registerUser = RegisterUserUseCase(
                        authRepository = application.container.authRepository,
                        userProfileRepository = application.container.userProfileRepository
                    )
                )
            }
        }
    }
}
