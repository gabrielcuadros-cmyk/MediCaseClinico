package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.CaseWidgetSummary
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus

/**
 * Construye el [CaseWidgetSummary] del widget a partir de los datos reales.
 *
 * Vive en el dominio y no en el widget para que sus reglas (clasificación
 * clínica, lectura desactualizada, contador sin negativos) se prueben en la
 * JVM y coincidan con las del Dashboard.
 *
 * @param outdatedAfterMs antigüedad a partir de la cual, sin enlace, una
 *   lectura deja de describir el estado de la insulina.
 */
class BuildCaseWidgetSummaryUseCase(
    private val outdatedAfterMs: Long = OUTDATED_READING_AFTER_MS
) {
    /**
     * @param latestReading última lectura guardada, o null si nunca hubo una.
     * @param connection estado actual del enlace con el estuche.
     * @param unreadAlerts alertas sin ver según el historial.
     * @param now hora actual (epoch ms), recibida para poder probarlo.
     */
    operator fun invoke(
        latestReading: TemperatureReading?,
        connection: ConnectionStatus,
        unreadAlerts: Int,
        now: Long
    ): CaseWidgetSummary {
        val alerts = unreadAlerts.coerceAtLeast(0)
        if (latestReading == null) {
            return CaseWidgetSummary.NoReadings(connection = connection, unreadAlerts = alerts)
        }
        return CaseWidgetSummary.Reading(
            celsius = latestReading.temperatureCelsius,
            status = TemperatureStatus.fromCelsius(latestReading.temperatureCelsius),
            timestamp = latestReading.timestamp,
            isOutdated = !connection.isLive && now - latestReading.timestamp > outdatedAfterMs,
            connection = connection,
            unreadAlerts = alerts
        )
    }

    companion object {
        /** Mismo límite que el Dashboard: un día sin enlace. */
        const val OUTDATED_READING_AFTER_MS = 24 * 60 * 60 * 1_000L
    }
}
