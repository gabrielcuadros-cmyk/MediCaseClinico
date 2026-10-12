package com.example.medicaseclinico.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.repository.AlertEventRepository
import com.example.medicaseclinico.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Si hay que pedir al usuario que inicie sesión antes de mostrar la app. */
enum class SessionState {
    /** Aún no se sabe si hay sesión guardada. */
    CHECKING,

    /** No hay sesión: se muestran Iniciar sesión, Crear cuenta y Recuperar contraseña. */
    SIGNED_OUT,

    SIGNED_IN,

    /** Esta instalación no tiene servicio de cuentas: se entra sin sesión. */
    NOT_REQUIRED
}

/**
 * Estado de la raíz de la app: lo que se ve en todas las pantallas a la vez.
 *
 * Es la compuerta de sesión y el contador de la pestaña Alertas. Este último
 * vive aquí y no en la pantalla de Alertas porque el indicador debe verse
 * desde cualquier pestaña.
 */
class MediCaseAppViewModel(
    alertEventRepository: AlertEventRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    /**
     * Decide entre las pantallas de cuenta y la app. Si el estado de la sesión
     * no se puede leer se trata como "sin sesión": es preferible pedir entrar
     * de nuevo que mostrar los datos del estuche a quien no se ha identificado.
     */
    val sessionState: StateFlow<SessionState> =
        if (!authRepository.isAvailable) {
            MutableStateFlow(SessionState.NOT_REQUIRED)
        } else {
            authRepository.currentUser
                .map { user -> if (user == null) SessionState.SIGNED_OUT else SessionState.SIGNED_IN }
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    emit(SessionState.SIGNED_OUT)
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = SessionState.CHECKING
                )
        }

    /**
     * Si falla, la sesión sigue abierta y la app no cambia: el `Result` se
     * descarta a propósito, el usuario puede volver a tocar el botón.
     */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /**
     * Alertas que el usuario aún no ha visto. Si no se puede leer el historial
     * no se muestra indicador: es preferible a un número que no es real, y el
     * error ya se explica al abrir la pestaña Alertas.
     */
    val unreadAlertsCount: StateFlow<Int> =
        alertEventRepository.observeUnreadCount()
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(NO_ALERTS)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = NO_ALERTS
            )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val NO_ALERTS = 0

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                MediCaseAppViewModel(
                    alertEventRepository = application.container.alertEventRepository,
                    authRepository = application.container.authRepository
                )
            }
        }
    }
}
