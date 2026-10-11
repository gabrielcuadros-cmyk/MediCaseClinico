package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.BatteryThresholds
import com.example.medicaseclinico.domain.model.HistoryEvent
import com.example.medicaseclinico.domain.model.TemperatureExcursion
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus

/**
 * Deduce la "Línea de eventos" del Historial Térmico a partir de las lecturas.
 *
 * Agrupa las lecturas fuera de rango consecutivas en un solo episodio para
 * que una excursión de varios minutos (muestreada cada 10 s) aparezca como
 * una alerta con su pico y no como decenas de filas repetidas.
 *
 * La batería usa histéresis, igual que las notificaciones: tras avisar, no
 * vuelve a avisar hasta que el nivel se recupera por encima de
 * [batteryRearmPercent]. Evita eventos duplicados cuando el nivel oscila
 * alrededor del umbral.
 */
class DetectTemperatureEventsUseCase(
    private val lowBatteryPercent: Int = BatteryThresholds.LOW_PERCENT,
    private val batteryRearmPercent: Int = BatteryThresholds.REARM_PERCENT
) {
    /**
     * @return eventos del más reciente al más antiguo (orden de la línea de
     * eventos); lista vacía si no hubo ninguno (la UI muestra Empty State).
     */
    operator fun invoke(readings: List<TemperatureReading>): List<HistoryEvent> {
        // El repositorio no guarda valores no finitos; se descartan aquí para
        // que un dato corrupto nunca se muestre como "pico" de una alerta.
        val sorted = readings
            .filter { it.temperatureCelsius.isFinite() }
            .sortedBy { it.timestamp }

        val events = detectTemperatureEvents(sorted) + detectBatteryEvents(sorted)
        return events.sortedByDescending { it.timestamp }
    }

    private fun detectTemperatureEvents(sorted: List<TemperatureReading>): List<HistoryEvent> {
        val events = mutableListOf<HistoryEvent>()
        var open: OpenExcursion? = null

        for (reading in sorted) {
            val direction = excursionOf(reading.temperatureCelsius)
            val current = open

            if (current != null && direction == current.direction) {
                current.extend(reading)
                continue
            }
            if (current != null) {
                events += current.toAlert(isClosed = true)
                if (direction == null) {
                    events += HistoryEvent.TemperatureNormalized(
                        timestamp = reading.timestamp,
                        celsius = reading.temperatureCelsius,
                        recoveredFrom = current.direction
                    )
                }
            }
            open = direction?.let { OpenExcursion(direction = it, first = reading) }
        }

        open?.let { events += it.toAlert(isClosed = false) }
        return events
    }

    private fun detectBatteryEvents(sorted: List<TemperatureReading>): List<HistoryEvent> {
        val events = mutableListOf<HistoryEvent>()
        var lowActive = false

        for (reading in sorted) {
            val level = reading.batteryLevel ?: continue
            when {
                level < lowBatteryPercent && !lowActive -> {
                    events += HistoryEvent.BatteryLow(
                        timestamp = reading.timestamp,
                        levelPercent = level
                    )
                    lowActive = true
                }
                level >= batteryRearmPercent -> lowActive = false
            }
        }
        return events
    }

    private fun excursionOf(celsius: Float): TemperatureExcursion? = when {
        !TemperatureStatus.fromCelsius(celsius).isAlert -> null
        celsius > TemperatureStatus.MAX_SAFE_CELSIUS -> TemperatureExcursion.HIGH
        else -> TemperatureExcursion.LOW
    }

    /** Episodio fuera de rango que todavía puede recibir más lecturas. */
    private class OpenExcursion(
        val direction: TemperatureExcursion,
        first: TemperatureReading
    ) {
        private val startTimestamp = first.timestamp
        private var lastTimestamp = first.timestamp
        private var peakCelsius = first.temperatureCelsius

        fun extend(reading: TemperatureReading) {
            lastTimestamp = reading.timestamp
            peakCelsius = when (direction) {
                TemperatureExcursion.HIGH -> maxOf(peakCelsius, reading.temperatureCelsius)
                TemperatureExcursion.LOW -> minOf(peakCelsius, reading.temperatureCelsius)
            }
        }

        fun toAlert(isClosed: Boolean) = HistoryEvent.TemperatureAlert(
            timestamp = startTimestamp,
            direction = direction,
            peakCelsius = peakCelsius,
            endTimestamp = lastTimestamp.takeIf { isClosed }
        )
    }
}
