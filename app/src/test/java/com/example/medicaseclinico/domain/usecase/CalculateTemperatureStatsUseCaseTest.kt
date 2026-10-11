package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.TemperatureReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class CalculateTemperatureStatsUseCaseTest {

    private val delta = 0.01f
    private val minute = TimeUnit.MINUTES.toMillis(1)

    private fun reading(celsius: Float, atMinute: Long) = TemperatureReading(
        temperatureCelsius = celsius,
        timestamp = atMinute * minute,
        isAlertTriggered = celsius !in 2f..8f
    )

    @Test
    fun `sin lecturas devuelve null para mostrar el estado vacio`() {
        val useCase = CalculateTemperatureStatsUseCase()
        assertNull(useCase(emptyList(), rangeEnd = 0L))
    }

    @Test
    fun `insulina estable a 4_2 grados esta 100 por ciento en rango`() {
        val useCase = CalculateTemperatureStatsUseCase(maxSampleGapMs = 60 * minute)
        val readings = listOf(reading(4.2f, 0), reading(4.2f, 10), reading(4.2f, 20))
        val stats = useCase(readings, rangeEnd = 30 * minute)!!

        assertEquals(100f, stats.timeInSafeRangePercent, delta)
        assertEquals(4.2f, stats.averageCelsius, delta)
        assertEquals(4.2f, stats.minCelsius, delta)
        assertEquals(4.2f, stats.maxCelsius, delta)
    }

    @Test
    fun `el porcentaje se pondera por tiempo y no por cantidad de muestras`() {
        val useCase = CalculateTemperatureStatsUseCase(maxSampleGapMs = 60 * minute)
        // 50 min seguros (una sola muestra) + 10 min críticos (diez muestras).
        val readings = listOf(reading(4.0f, 0)) +
                (50L until 60L).map { reading(9.0f, it) }

        val stats = useCase(readings, rangeEnd = 60 * minute)!!

        // Por muestras serían 1/11 ≈ 9 %; por tiempo, 50/60 ≈ 83.3 %.
        assertEquals(83.33f, stats.timeInSafeRangePercent, delta)
        assertEquals(11, stats.sampleCount)
    }

    @Test
    fun `los huecos sin vigilancia no cuentan como tiempo seguro`() {
        val useCase = CalculateTemperatureStatsUseCase(maxSampleGapMs = 5 * minute)
        // Lectura segura y 2 horas después una crítica: el hueco se limita a 5 min.
        val readings = listOf(reading(4.0f, 0), reading(9.0f, 120))

        val stats = useCase(readings, rangeEnd = 125 * minute)!!

        assertEquals(50f, stats.timeInSafeRangePercent, delta)
        assertEquals(10 * minute, stats.monitoredDurationMs)
    }

    @Test
    fun `minimo maximo y media ponderada con lecturas desordenadas`() {
        val useCase = CalculateTemperatureStatsUseCase(maxSampleGapMs = 60 * minute)
        val readings = listOf(reading(4.0f, 20), reading(4.0f, 0), reading(9.0f, 10))

        val stats = useCase(readings, rangeEnd = 30 * minute)!!

        assertEquals(4.0f, stats.minCelsius, delta)
        assertEquals(9.0f, stats.maxCelsius, delta)
        assertEquals(5.67f, stats.averageCelsius, delta)
        assertEquals(66.67f, stats.timeInSafeRangePercent, delta)
    }
}