package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.AlertEvent

/**
 * Últimas alertas para el widget "Últimas alertas" de la pantalla de inicio.
 *
 * @param alerts las más recientes primero, como mucho las que caben en el widget.
 * @param unreadCount todas las alertas sin ver, también las que no entran en la
 *   lista: es el mismo número que la insignia de la pestaña Alertas.
 */
data class RecentAlertsSummary(
    val alerts: List<AlertEvent>,
    val unreadCount: Int
)

/**
 * Elige qué alertas muestra el widget. Está en el dominio para probar el orden,
 * el límite y el contador en la JVM, sin depender de cómo las entregue Room.
 *
 * @param limit cuántas alertas como máximo; un widget no es la pantalla completa.
 */
class SelectRecentAlertsUseCase(private val limit: Int = DEFAULT_LIMIT) {

    init {
        require(limit > 0) { "El widget debe poder mostrar al menos una alerta" }
    }

    operator fun invoke(events: List<AlertEvent>): RecentAlertsSummary = RecentAlertsSummary(
        alerts = events.sortedByDescending { it.timestamp }.take(limit),
        unreadCount = events.count { !it.isRead }
    )

    companion object {
        const val DEFAULT_LIMIT = 10
    }
}
