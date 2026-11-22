package com.example.medicaseclinico.ui.widget.alerts

import com.example.medicaseclinico.di.AppContainer
import com.example.medicaseclinico.domain.model.AlertEvent
import com.example.medicaseclinico.domain.repository.AlertEventRepository
import com.example.medicaseclinico.domain.time.DateTimeLabels
import com.example.medicaseclinico.domain.time.EventTimeLabel
import com.example.medicaseclinico.domain.usecase.RecentAlertsSummary
import com.example.medicaseclinico.domain.usecase.SelectRecentAlertsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Una fila del widget: la alerta guardada y su hora ya clasificada (hoy / ayer / antes). */
data class RecentAlertItem(
    val event: AlertEvent,
    val time: EventTimeLabel
)

/**
 * Estado del widget "Últimas alertas": mismo patrón que las pantallas
 * (Loading / Error / datos). El estado vacío es [Ready] con la lista vacía.
 */
sealed interface RecentAlertsUiState {
    data object Loading : RecentAlertsUiState

    /** No se pudo leer el historial: el widget ofrece "Reintentar". */
    data object Error : RecentAlertsUiState

    data class Ready(
        val items: List<RecentAlertItem>,
        val unreadCount: Int
    ) : RecentAlertsUiState
}

/**
 * Única puerta del widget al historial de alertas (Room). Las alertas son las
 * mismas que ve la pantalla "Alertas de Seguridad": nada se inventa aquí.
 */
class RecentAlertsStateSource(
    private val alertEventRepository: AlertEventRepository,
    private val selectRecentAlerts: SelectRecentAlertsUseCase = SelectRecentAlertsUseCase(),
    private val dateTimeLabels: DateTimeLabels = DateTimeLabels.system(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    /** Lo que el widget muestra; también decide cuándo redibujarlo. */
    fun summaries(): Flow<RecentAlertsSummary> =
        alertEventRepository.observeAlertEvents().map { selectRecentAlerts(it) }

    /** Un flujo nuevo en cada llamada: "Reintentar" vuelve a leer desde cero. */
    fun uiState(): Flow<RecentAlertsUiState> = summaries()
        .map { summary ->
            val now = clock()
            RecentAlertsUiState.Ready(
                items = summary.alerts.map { event ->
                    RecentAlertItem(
                        event = event,
                        time = dateTimeLabels.eventTime(timestamp = event.timestamp, referenceTime = now)
                    )
                },
                unreadCount = summary.unreadCount
            )
        }
        .catch<RecentAlertsUiState> { error ->
            if (error is CancellationException) throw error
            emit(RecentAlertsUiState.Error)
        }

    companion object {
        /** Con las instancias únicas de la app. */
        fun from(container: AppContainer): RecentAlertsStateSource =
            RecentAlertsStateSource(alertEventRepository = container.alertEventRepository)
    }
}
