package com.example.medicaseclinico.domain.usecase

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

    companion object {
        val DEFAULT_MAX_SAMPLE_GAP_MS: Long = TimeUnit.MINUTES.toMillis(5)
    }
}