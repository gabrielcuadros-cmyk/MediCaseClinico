package com.example.medicaseclinico.ui.screens.users.add

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
import com.example.medicaseclinico.domain.model.AccessStatus
import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.UserPermission
import com.example.medicaseclinico.domain.model.UserPermissions
import com.example.medicaseclinico.domain.model.UserRole
import com.example.medicaseclinico.domain.model.with
import com.example.medicaseclinico.domain.repository.AuthRepository
import com.example.medicaseclinico.domain.repository.AuthorizedUsersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Errores de un campo que se detectan en el teléfono, antes de guardar. */
enum class AddUserFieldError {
    NAME_REQUIRED,
    EMAIL_REQUIRED,
    EMAIL_INVALID,
    PHONE_REQUIRED,
    PHONE_INVALID
}

/** Por qué no se pudo guardar a la persona. */
enum class AddUserError {
    /** No hay sesión: no existe un dueño a cuya lista añadirla. */
    NO_SESSION,

    /** La nube rechazó o no pudo recibir el registro. */
    SAVE_FAILED
}

/**
 * Persona ya guardada cuya invitación falta enviar: la pantalla abre con ella
 * el menú de compartir de Android.
 */
@Immutable
data class PendingInvitation(
    val name: String,
    val role: UserRole
)

/**
 * Estado del formulario "Nuevo Usuario". Es un formulario siempre visible: lo
 * que cambia son sus errores, el rol, los permisos y si se está guardando.
 *
 * @param permissions permisos con los que empezará la persona. Parten de los
 * del rol y el dueño puede ajustarlos antes de invitar.
 * @param invitation distinto de null cuando la persona quedó guardada.
 */
@Immutable
data class AddUserUiState(
    val nameError: AddUserFieldError? = null,
    val emailError: AddUserFieldError? = null,
    val phoneError: AddUserFieldError? = null,
    val role: UserRole = DEFAULT_ROLE,
    val permissions: UserPermissions = UserPermissions.defaultsFor(DEFAULT_ROLE),
    val isSubmitting: Boolean = false,
    val error: AddUserError? = null,
    val invitation: PendingInvitation? = null
) {
    val hasFieldErrors: Boolean
        get() = nameError != null || emailError != null || phoneError != null

    companion object {
        /** Rol con el que abre el formulario, como en el diseño. */
        val DEFAULT_ROLE = UserRole.CAREGIVER
    }
}

/**
 * Valida el formulario y registra a la persona como invitación pendiente.
 *
 * La app no envía la invitación por su cuenta: tras guardar, la pantalla abre
 * el menú de compartir para que el dueño la mande por donde prefiera. Que el
 * invitado entre desde su propio teléfono es una mejora futura.
 */
class AddUserViewModel(
    private val authRepository: AuthRepository,
    private val authorizedUsersRepository: AuthorizedUsersRepository
) : ViewModel() {

    // Los textos son estado de Compose y no parte del StateFlow, para que los
    // campos no pierdan letras al escribir rápido.
    var name by mutableStateOf("")
        private set
    var email by mutableStateOf("")
        private set
    var phone by mutableStateOf("")
        private set

    private val _uiState = MutableStateFlow(AddUserUiState())
    val uiState: StateFlow<AddUserUiState> = _uiState.asStateFlow()

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

    /**
     * Cambiar de rol vuelve a los permisos de fábrica de ese rol: son el punto
     * de partida razonable y evitan dejar a un cuidador con permisos de
     * administrador por un cambio de idea.
     */
    fun onRoleSelected(role: UserRole) {
        _uiState.update {
            if (it.role == role) it else it.copy(role = role, permissions = UserPermissions.defaultsFor(role))
        }
    }

    fun onPermissionToggled(permission: UserPermission, granted: Boolean) {
        _uiState.update { it.copy(permissions = it.permissions.with(permission, granted)) }
    }

    fun sendInvitation() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val validated = current.copy(
            nameError = if (AuthValidation.isValidName(name)) null else AddUserFieldError.NAME_REQUIRED,
            emailError = when {
                email.isBlank() -> AddUserFieldError.EMAIL_REQUIRED
                !AuthValidation.isValidEmail(email) -> AddUserFieldError.EMAIL_INVALID
                else -> null
            },
            phoneError = when {
                phone.isBlank() -> AddUserFieldError.PHONE_REQUIRED
                !AuthValidation.isValidPhone(phone) -> AddUserFieldError.PHONE_INVALID
                else -> null
            },
            error = null
        )
        if (validated.hasFieldErrors) {
            _uiState.value = validated
            return
        }

        _uiState.value = validated.copy(isSubmitting = true)
        viewModelScope.launch {
            val user = AuthorizedUser(
                name = name.trim(),
                email = email.trim(),
                phone = AuthValidation.normalizePhone(phone),
                role = validated.role,
                permissions = validated.permissions,
                status = AccessStatus.INVITATION_PENDING
            )
            val ownerId = currentOwnerId()
            val failure = when {
                ownerId == null -> AddUserError.NO_SESSION
                authorizedUsersRepository.saveUser(ownerId = ownerId, user = user).isFailure ->
                    AddUserError.SAVE_FAILED
                else -> null
            }
            _uiState.update {
                if (failure == null) {
                    it.copy(
                        isSubmitting = false,
                        invitation = PendingInvitation(name = user.name, role = user.role)
                    )
                } else {
                    it.copy(isSubmitting = false, error = failure)
                }
            }
        }
    }

    /** La pantalla ya abrió el menú de compartir: no debe repetirse al girar el teléfono. */
    fun onInvitationHandled() {
        _uiState.update { it.copy(invitation = null) }
    }

    private suspend fun currentOwnerId(): String? =
        try {
            authRepository.currentUser.first()?.uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                AddUserViewModel(
                    authRepository = application.container.authRepository,
                    authorizedUsersRepository = application.container.authorizedUsersRepository
                )
            }
        }
    }
}
