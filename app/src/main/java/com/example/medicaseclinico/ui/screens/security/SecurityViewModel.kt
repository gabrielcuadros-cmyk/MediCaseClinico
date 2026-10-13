package com.example.medicaseclinico.ui.screens.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.model.AppearanceMode
import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.CaseCommandError
import com.example.medicaseclinico.domain.model.CaseCommandException
import com.example.medicaseclinico.domain.model.CaseControlState
import com.example.medicaseclinico.domain.repository.AppearanceRepository
import com.example.medicaseclinico.domain.repository.AuthRepository
import com.example.medicaseclinico.domain.repository.AuthorizedUsersRepository
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.user.initialsOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Prepara la pantalla "Seguridad del Estuche": el seguro y la contraseña del
 * estuche (órdenes por Bluetooth) y las personas con acceso (en la nube).
 *
 * La contraseña pasa por aquí solo de camino al estuche: no se guarda en el
 * estado, ni en el teléfono, ni en la nube.
 */
class SecurityViewModel(
    private val deviceRepository: CaseDeviceRepository,
    private val authRepository: AuthRepository,
    private val authorizedUsersRepository: AuthorizedUsersRepository,
    private val appearanceRepository: AppearanceRepository
) : ViewModel() {

    /** Lo que no viene de los repositorios: trabajo en curso y aviso pendiente. */
    private data class Local(
        val isBusy: Boolean = false,
        val message: SecurityMessage? = null
    )

    /** La lista de personas, o que no se pudo leer. */
    private data class Users(val list: List<AuthorizedUser>, val failed: Boolean)

    private val local = MutableStateFlow(Local())
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SecurityUiState> =
        retryTrigger
            .flatMapLatest { observeScreen() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = SecurityUiState.Loading
            )

    /** Activa o desactiva el seguro. El estuche exige su contraseña actual para ello. */
    fun setPinRequired(required: Boolean, currentPin: String) = runCaseCommand(
        success = if (required) SecurityMessage.LOCK_ENABLED else SecurityMessage.LOCK_DISABLED
    ) {
        deviceRepository.setPinRequired(required = required, currentPin = currentPin)
    }

    fun changePin(currentPin: String, newPin: String) = runCaseCommand(
        success = SecurityMessage.PIN_CHANGED
    ) {
        deviceRepository.changePin(currentPin = currentPin, newPin = newPin)
    }

    /** Quita el acceso a una persona. La pantalla ya pidió confirmación. */
    fun removeUser(userId: String) {
        if (local.value.isBusy) return
        local.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val ownerId = currentOwnerId()
            val result = if (ownerId == null) {
                Result.failure(IllegalStateException("Sin sesión"))
            } else {
                authorizedUsersRepository.deleteUser(ownerId = ownerId, userId = userId)
            }
            local.value = Local(
                message = if (result.isSuccess) {
                    SecurityMessage.USER_REMOVED
                } else {
                    SecurityMessage.USER_REMOVE_FAILED
                }
            )
        }
    }

    /**
     * Cambia la apariencia de la app. No espera a nada: la raíz observa la
     * misma preferencia y repinta en cuanto queda guardada.
     */
    fun setAppearance(mode: AppearanceMode) {
        viewModelScope.launch {
            if (appearanceRepository.setMode(mode).isFailure) {
                local.update { it.copy(message = SecurityMessage.APPEARANCE_NOT_SAVED) }
            }
        }
    }

    /** La pantalla ya mostró el aviso: no debe repetirse al girar el teléfono. */
    fun onMessageShown() {
        local.update { it.copy(message = null) }
    }

    /** Vuelve a leer el estuche y la lista de personas tras un error. */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

    private fun runCaseCommand(
        success: SecurityMessage,
        command: suspend () -> Result<Unit>
    ) {
        if (local.value.isBusy) return
        local.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val message = command().fold(
                onSuccess = { success },
                onFailure = { failure ->
                    ((failure as? CaseCommandException)?.error ?: CaseCommandError.FAILED).toMessage()
                }
            )
            local.value = Local(message = message)
        }
    }

    private fun observeScreen(): Flow<SecurityUiState> =
        combine(
            deviceRepository.controlState,
            observeUsers(),
            observeAppearance(),
            local
        ) { control, users, appearance, local ->
            buildUiState(control, users, appearance, local)
        }
            .onStart { emit(SecurityUiState.Loading) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(SecurityUiState.Error)
            }

    private fun buildUiState(
        control: CaseControlState,
        users: Users,
        appearance: AppearanceMode,
        local: Local
    ): SecurityUiState = SecurityUiState.Success(
        pin = control.toPinState(),
        users = users.list.map { user ->
            AccessUser(
                id = user.id,
                name = user.name,
                initials = initialsOf(user.name),
                role = user.role
            )
        },
        usersFailed = users.failed,
        appearance = appearance,
        isBusy = local.isBusy,
        message = local.message
    )

    /**
     * La lista depende de quién tenga la sesión. Si no se puede leer (sin
     * permiso en la nube, sin servicio) se marca como fallida en lugar de
     * tumbar la pantalla entera.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeUsers(): Flow<Users> =
        authRepository.currentUser
            .map { it?.uid }
            .distinctUntilChanged()
            .flatMapLatest { ownerId ->
                if (ownerId == null) {
                    flowOf(emptyList())
                } else {
                    authorizedUsersRepository.observeUsers(ownerId)
                }
            }
            .map { Users(list = it, failed = false) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(Users(list = emptyList(), failed = true))
            }

    /** Si la preferencia no se puede leer, se muestra la de fábrica en vez de tumbar la pantalla. */
    private fun observeAppearance(): Flow<AppearanceMode> =
        appearanceRepository.mode.catch { throwable ->
            if (throwable is CancellationException) throw throwable
            emit(AppearanceMode.DEFAULT)
        }

    private suspend fun currentOwnerId(): String? =
        try {
            authRepository.currentUser.first()?.uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun CaseControlState.toPinState(): CasePinState = when (this) {
        CaseControlState.Unavailable ->
            CasePinState(availability = CasePinAvailability.DISCONNECTED, pinRequired = null)
        CaseControlState.NotSupported ->
            CasePinState(availability = CasePinAvailability.NOT_SUPPORTED, pinRequired = null)
        is CaseControlState.Ready ->
            CasePinState(availability = CasePinAvailability.READY, pinRequired = pinRequired)
    }

    private fun CaseCommandError.toMessage(): SecurityMessage = when (this) {
        CaseCommandError.NOT_CONNECTED -> SecurityMessage.CASE_NOT_CONNECTED
        CaseCommandError.NOT_SUPPORTED -> SecurityMessage.CASE_NOT_SUPPORTED
        CaseCommandError.PAIRING_REQUIRED -> SecurityMessage.PAIRING_REQUIRED
        CaseCommandError.WRONG_PIN -> SecurityMessage.WRONG_PIN
        CaseCommandError.LOCKED_OUT -> SecurityMessage.CASE_LOCKED_OUT
        CaseCommandError.INVALID_VALUE -> SecurityMessage.INVALID_PIN
        CaseCommandError.FAILED -> SecurityMessage.CASE_FAILED
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                SecurityViewModel(
                    deviceRepository = application.container.caseDeviceRepository,
                    authRepository = application.container.authRepository,
                    authorizedUsersRepository = application.container.authorizedUsersRepository,
                    appearanceRepository = application.container.appearanceRepository
                )
            }
        }
    }
}
