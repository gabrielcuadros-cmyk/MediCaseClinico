package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.BatteryThresholds
import com.example.medicaseclinico.domain.model.HistoryEvent
import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureExcursion
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus
import java.util.concurrent.TimeUnit

/**
 * Deduce la "Línea de eventos" del Historial Térmico a partir de las lecturas.
 *
 * Agrupa las lecturas fuera de rango consecutivas en un solo episodio para
 * que una excursión de varios minutos (muestreada cada 10 s) aparezca como
 * una alerta con su pico y no como decenas de filas repetidas.
 *
 * Antirrebote: si la temperatura vuelve al rango menos de [mergeGapMs] y sale
 * otra vez en el mismo sentido, sigue siendo el mismo episodio. Una insulina
 * que oscila alrededor de 8 °C es un único problema clínico, no diez alertas.
 * Alta y baja nunca se unen entre sí porque son riesgos distintos.
 *
 * La batería usa histéresis, igual que las notificaciones: tras avisar, no
 * vuelve a avisar hasta que el nivel se recupera por encima de
 * [batteryRearmPercent]. Evita eventos duplicados cuando el nivel oscila
 * alrededor del umbral.
 */
class DetectTemperatureEventsUseCase(
    private val lowBatteryPercent: Int = BatteryThresholds.LOW_PERCENT,
    private val batteryRearmPercent: Int = BatteryThresholds.REARM_PERCENT,
    private val mergeGapMs: Long = DEFAULT_MERGE_GAP_MS
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

    /**
     * Mismos eventos a partir de lecturas ya resumidas por intervalos.
     *
     * Un intervalo cuenta como fuera de rango si su máximo o su mínimo lo
     * están, aunque su media sea normal: así un pico breve no desaparece al
     * promediar. Las horas de los eventos tienen la precisión del intervalo.
     */
    fun fromBuckets(buckets: List<TemperatureBucket>): List<HistoryEvent> =
        invoke(buckets.flatMap { it.toRepresentativeReadings() })

    /**
     * Convierte el intervalo en las lecturas que lo representan: sus extremos
     * fuera de rango (alta antes que baja) o, si todo estuvo en rango, su media.
     */
    private fun TemperatureBucket.toRepresentativeReadings(): List<TemperatureReading> {
        val extremes = listOfNotNull(
            maxCelsius.takeIf { it > TemperatureStatus.MAX_SAFE_CELSIUS },
            minCelsius.takeIf { it < TemperatureStatus.MIN_SAFE_CELSIUS }
        )
        return extremes.ifEmpty { listOf(averageCelsius) }.mapIndexed { index, celsius ->
            TemperatureReading(
                temperatureCelsius = celsius,
                timestamp = startTimestamp,
                isAlertTriggered = extremes.isNotEmpty(),
                batteryLevel = lastBatteryLevel.takeIf { index == 0 }
            )
        }
    }

    private fun detectTemperatureEvents(sorted: List<TemperatureReading>): List<HistoryEvent> {
        val events = mutableListOf<HistoryEvent>()
        var open: OpenExcursion? = null

        for (reading in sorted) {
            val direction = excursionOf(reading.temperatureCelsius)
            val current = open

            when {
                current == null ->
                    open = direction?.let { OpenExcursion(direction = it, first = reading) }
                direction == null -> current.markReturn(reading)
                direction == current.direction && current.canAbsorb(reading, mergeGapMs) ->
                    current.extend(reading)
                else -> {
                    events += current.toEvents(isSuperseded = true)
                    open = OpenExcursion(direction = direction, first = reading)
                }
            }
        }

        open?.let { events += it.toEvents(isSuperseded = false) }
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

        /** Primera lectura en rango tras la última fuera de rango, si la hay. */
        private var returnReading: TemperatureReading? = null

        fun markReturn(reading: TemperatureReading) {
            if (returnReading == null) returnReading = reading
        }

        /**
         * true si [reading] continúa este episodio: o nunca se volvió al
         * rango, o el regreso duró menos que [mergeGapMs] (rebote en el límite).
         */
        fun canAbsorb(reading: TemperatureReading, mergeGapMs: Long): Boolean =
            returnReading == null || reading.timestamp - lastTimestamp < mergeGapMs

        fun extend(reading: TemperatureReading) {
            returnReading = null
            lastTimestamp = reading.timestamp
            peakCelsius = when (direction) {
                TemperatureExcursion.HIGH -> maxOf(peakCelsius, reading.temperatureCelsius)
                TemperatureExcursion.LOW -> minOf(peakCelsius, reading.temperatureCelsius)
            }
        }

        /**
         * @param isSuperseded true si otro episodio empieza después de este;
         * si no, la alerta solo se cierra cuando hubo regreso al rango.
         */
        fun toEvents(isSuperseded: Boolean): List<HistoryEvent> {
            val returned = returnReading
            val alert = HistoryEvent.TemperatureAlert(
                timestamp = startTimestamp,
                direction = direction,
                peakCelsius = peakCelsius,
                endTimestamp = lastTimestamp.takeIf { isSuperseded || returned != null }
            )
            val normalized = returned?.let {
                HistoryEvent.TemperatureNormalized(
                    timestamp = it.timestamp,
                    celsius = it.temperatureCelsius,
                    recoveredFrom = direction
                )
            }
            return listOfNotNull(alert, normalized)
        }
    }

    companion object {
        /** Regreso al rango más corto que esto se considera un rebote en el límite. */
        val DEFAULT_MERGE_GAP_MS: Long = TimeUnit.MINUTES.toMillis(5)
    }
}
