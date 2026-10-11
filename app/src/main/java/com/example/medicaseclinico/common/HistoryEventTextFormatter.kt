package com.example.medicaseclinico.common

import android.content.res.Resources
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.HistoryEvent
import com.example.medicaseclinico.domain.model.TemperatureExcursion

/**
 * Convierte un [HistoryEvent] en su título y descripción visibles.
 *
 * Lo comparten la línea de eventos de la pantalla y el Reporte Médico (PDF):
 * con un solo lugar, el médico lee en el documento exactamente las mismas
 * palabras que el paciente vio en la app, y añadir un tipo de evento nuevo
 * obliga al compilador a pedir sus textos aquí y solo aquí.
 *
 * Está fuera de `ui` y de `data` porque ambas capas lo usan, y fuera de
 * `domain` porque necesita los recursos de Android.
 */
class HistoryEventTextFormatter(private val resources: Resources) {

    fun title(event: HistoryEvent): String = when (event) {
        is HistoryEvent.TemperatureAlert -> resources.getString(R.string.history_event_alert_title)
        is HistoryEvent.TemperatureNormalized ->
            resources.getString(R.string.history_event_normalized_title)
        is HistoryEvent.BatteryLow -> resources.getString(R.string.history_event_battery_title)
    }

    fun description(event: HistoryEvent): String = when (event) {
        is HistoryEvent.TemperatureAlert -> {
            val peak = celsius(event.peakCelsius)
            val peakDescription = when (event.direction) {
                TemperatureExcursion.HIGH ->
                    resources.getString(R.string.history_event_alert_high, peak)
                TemperatureExcursion.LOW ->
                    resources.getString(R.string.history_event_alert_low, peak)
            }
            if (event.endTimestamp == null) {
                resources.getString(R.string.history_event_alert_ongoing, peakDescription)
            } else {
                peakDescription
            }
        }
        is HistoryEvent.TemperatureNormalized -> resources.getString(
            R.string.history_event_normalized_description,
            celsius(event.celsius)
        )
        is HistoryEvent.BatteryLow ->
            resources.getString(R.string.history_event_battery_description, event.levelPercent)
    }

    private fun celsius(value: Float): String =
        resources.getString(R.string.temperature_value, value)
}
