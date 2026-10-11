package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStats
import com.example.medicaseclinico.domain.model.TemperatureStatus
import java.util.concurrent.TimeUnit

/**
 * Calcula las estadísticas térmicas de un conjunto de lecturas con ponderación por tiempo.
 */
class CalculateTemperatureStatsUseCase(
    private val maxSampleGapMs: Long = DEFAULT_MAX_SAMPLE_GAP_MS
) {
    /** @return estadísticas, o null si no hay lecturas (la UI muestra Empty State). */
    operator fun invoke(readings: List<TemperatureReading>, rangeEnd: Long): TemperatureStats? {
        if (readings.isEmpty()) return null

        val sorted = readings.sortedBy { it.timestamp }
        var weightedSum = 0.0
        var totalWeightMs = 0L
        var safeWeightMs = 0L
        var safeSampleCount = 0

        sorted.forEachIndexed { index, reading ->
            val nextTimestamp = sorted.getOrNull(index + 1)?.timestamp ?: rangeEnd
            val weightMs = (nextTimestamp - reading.timestamp).coerceIn(0L, maxSampleGapMs)
            val isSafe = !TemperatureStatus.fromCelsius(reading.temperatureCelsius).isAlert

            weightedSum += reading.temperatureCelsius.toDouble() * weightMs
            totalWeightMs += weightMs

            if (isSafe) {
                safeWeightMs += weightMs
                safeSampleCount++
            }
        }

        val average = if (totalWeightMs > 0) {
            weightedSum / totalWeightMs
        } else {
            sorted.map { it.temperatureCelsius.toDouble() }.average()
        }

        val safePercent = if (totalWeightMs > 0) {
            safeWeightMs * 100f / totalWeightMs
        } else {
            safeSampleCount * 100f / sorted.size
        }

        return TemperatureStats(
            sampleCount = sorted.size,
            minCelsius = sorted.minOf { it.temperatureCelsius },
            maxCelsius = sorted.maxOf { it.temperatureCelsius },
            averageCelsius = average.toFloat(),
            timeInSafeRangePercent = safePercent.coerceIn(0f, 100f),
            monitoredDurationMs = totalWeightMs
        )
    }

    /**
     * Mismas estadísticas a partir de lecturas ya resumidas por intervalos
     * (rangos largos, donde cargar cada lectura sería demasiado lento).
     *
     * Cada intervalo pesa el tiempo hasta el siguiente, con tope de
     * [bucketMs]: los intervalos sin lecturas (estuche desconectado) no
     * cuentan como tiempo vigilado. Mínimo y máximo salen de los extremos
     * reales de cada intervalo, no de su media, para no ocultar picos.
     *
     * @return estadísticas, o null si no hay lecturas (la UI muestra Empty State).
     */
    fun fromBuckets(
        buckets: List<TemperatureBucket>,
        bucketMs: Long,
        rangeEnd: Long
    ): TemperatureStats? {
        val sorted = buckets.filter { it.sampleCount > 0 }.sortedBy { it.startTimestamp }
        if (sorted.isEmpty()) return null

        var weightedSum = 0.0
        var safeWeightMs = 0.0
        var totalWeightMs = 0L
        var sampleCount = 0
        var safeSampleCount = 0

        sorted.forEachIndexed { index, bucket ->
            val nextStart = sorted.getOrNull(index + 1)?.startTimestamp ?: rangeEnd
            val weightMs = (nextStart - bucket.startTimestamp).coerceIn(0L, bucketMs)
            val safeSamples = (bucket.sampleCount - bucket.outOfRangeCount).coerceAtLeast(0)

            weightedSum += bucket.averageCelsius.toDouble() * weightMs
            safeWeightMs += weightMs * safeSamples.toDouble() / bucket.sampleCount
            totalWeightMs += weightMs
            sampleCount += bucket.sampleCount
            safeSampleCount += safeSamples
        }

        val average = if (totalWeightMs > 0) {
            weightedSum / totalWeightMs
        } else {
            sorted.sumOf { it.averageCelsius.toDouble() * it.sampleCount } / sampleCount
        }

        val safePercent = if (totalWeightMs > 0) {
            (safeWeightMs * 100 / totalWeightMs).toFloat()
        } else {
            safeSampleCount * 100f / sampleCount
        }

        return TemperatureStats(
            sampleCount = sampleCount,
            minCelsius = sorted.minOf { it.minCelsius },
            maxCelsius = sorted.maxOf { it.maxCelsius },
            averageCelsius = average.toFloat(),
            timeInSafeRangePercent = safePercent.coerceIn(0f, 100f),
            monitoredDurationMs = totalWeightMs
        )
    }

    companion object {
        val DEFAULT_MAX_SAMPLE_GAP_MS: Long = TimeUnit.MINUTES.toMillis(5)
    }
}