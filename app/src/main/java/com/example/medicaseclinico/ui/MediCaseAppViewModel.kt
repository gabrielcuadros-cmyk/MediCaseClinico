package com.example.medicaseclinico.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.repository.AlertEventRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.cancellation.CancellationException

/**
 * Estado de la raíz de la app: lo que se ve en todas las pantallas a la vez.
 *
 * Hoy es solo el contador de la pestaña Alertas. Vive aquí y no en la pantalla
 * de Alertas porque el indicador debe verse desde cualquier pestaña.
 */
class MediCaseAppViewModel(
    alertEventRepository: AlertEventRepository
) : ViewModel() {

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
                    alertEventRepository = application.container.alertEventRepository
                )
            }
        }
    }
}
