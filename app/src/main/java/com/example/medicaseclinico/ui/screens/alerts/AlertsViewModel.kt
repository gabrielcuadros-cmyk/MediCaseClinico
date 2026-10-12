package com.example.medicaseclinico.ui.screens.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.model.AlertEvent
import com.example.medicaseclinico.domain.model.AlertFilter
import com.example.medicaseclinico.domain.model.RelativeTime
import com.example.medicaseclinico.domain.repository.AlertEventRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

/**
 * Prepara la lista de "Alertas de Seguridad" desde el historial de Room.
 *
 * El filtro se aplica en memoria sobre la misma consulta: cambiar de chip no
 * vuelve a leer la base de datos ni pasa por el estado de carga.
 *
 * Mientras la pantalla está abierta, las alertas se marcan como vistas, también
 * las que llegan en ese momento: el usuario las tiene delante y el contador de
 * la pestaña no debe seguir avisando de ellas.
 *
 * @param clock se inyecta para que los tests fijen la hora del tiempo relativo.
 * @param tickIntervalMs cada cuánto se recalcula "Hace X min" aunque no
 * lleguen alertas nuevas.
 */
class AlertsViewModel(
    private val alertEventRepository: AlertEventRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tickIntervalMs: Long = DEFAULT_TICK_INTERVAL_MS
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(AlertFilter.DEFAULT)
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AlertsUiState> =
        retryTrigger
            .flatMapLatest { observeAlerts() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AlertsUiState.Loading
            )

    /** El usuario tocó un chip de filtro. */
    fun onFilterSelected(filter: AlertFilter) {
        selectedFilter.value = filter
    }

    /** Vuelve a consultar el historial tras un error. */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

    private fun observeAlerts(): Flow<AlertsUiState> =
        combine(
            alertEventRepository.observeAlertEvents().onEach { events -> markAsRead(events) },
            selectedFilter,
            currentTime()
        ) { events, filter, now ->
            buildUiState(events, filter, now)
        }
            .onStart { emit(AlertsUiState.Loading) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(AlertsUiState.Error)
            }

    /**
     * Si falla, las alertas se muestran igual y el contador se queda como
     * estaba: el `Result` se descarta a propósito.
     */
    private suspend fun markAsRead(events: List<AlertEvent>) {
        if (events.any { !it.isRead }) alertEventRepository.markAllAsRead()
    }

    private fun currentTime(): Flow<Long> = flow {
        while (true) {
            emit(clock())
            delay(tickIntervalMs)
        }
    }

    private fun buildUiState(
        events: List<AlertEvent>,
        filter: AlertFilter,
        now: Long
    ): AlertsUiState {
        val alerts = events
            .filter { filter.matches(it.type) }
            .map { event ->
                AlertItem(
                    id = event.id,
                    type = event.type,
                    value = event.value,
                    elapsed = RelativeTime.between(timestamp = event.timestamp, now = now),
                    detail = event.detail?.takeIf { it.isNotBlank() }
                )
            }
        return if (alerts.isEmpty()) {
            AlertsUiState.Empty(selectedFilter = filter)
        } else {
            AlertsUiState.Success(selectedFilter = filter, alerts = alerts)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** El tiempo relativo cambia como mucho una vez por minuto. */
        const val DEFAULT_TICK_INTERVAL_MS = 60_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                AlertsViewModel(
                    alertEventRepository = application.container.alertEventRepository
                )
            }
        }
    }
}
