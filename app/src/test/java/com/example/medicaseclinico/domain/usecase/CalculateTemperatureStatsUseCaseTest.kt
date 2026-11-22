package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus
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
        isAlertTriggered = TemperatureStatus.fromCelsius(celsius).isAlert
    )

    private val bucketMs = 2 * minute

    private fun bucket(
        index: Long,
        average: Float,
        min: Float = average,
        max: Float = average,
        samples: Int = 12,
        outOfRange: Int = 0
    ) = TemperatureBucket(
        startTimestamp = index * bucketMs,
        averageCelsius = average,
        minCelsius = min,
        maxCelsius = max,
        sampleCount = samples,
        outOfRangeCount = outOfRange
    )

    @Test
    fun `sin intervalos devuelve null para mostrar el estado vacio`() {
        val useCase = CalculateTemperatureStatsUseCase()
        assertNull(useCase.fromBuckets(emptyList(), bucketMs = bucketMs, rangeEnd = 0L))
    }

    @Test
    fun `intervalos estables a 22_2 grados estan 100 por ciento en rango`() {
        val useCase = CalculateTemperatureStatsUseCase()
        val buckets = listOf(bucket(0, 22.2f), bucket(1, 22.2f), bucket(2, 22.2f))

        val stats = useCase.fromBuckets(buckets, bucketMs = bucketMs, rangeEnd = 3 * bucketMs)!!

        assertEquals(100f, stats.timeInSafeRangePercent, delta)
        assertEquals(22.2f, stats.averageCelsius, delta)
        assertEquals(36, stats.sampleCount)
        assertEquals(3 * bucketMs, stats.monitoredDurationMs)
    }

    @Test
    fun `minimo y maximo salen de los extremos del intervalo y no de su media`() {
        val useCase = CalculateTemperatureStatsUseCase()
        val buckets = listOf(bucket(0, 23.0f, min = 14.5f, max = 30.4f, outOfRange = 2), bucket(1, 22.0f))

        val stats = useCase.fromBuckets(buckets, bucketMs = bucketMs, rangeEnd = 2 * bucketMs)!!

        assertEquals(14.5f, stats.minCelsius, delta)
        assertEquals(30.4f, stats.maxCelsius, delta)
        assertEquals(22.5f, stats.averageCelsius, delta)
    }

    @Test
    fun `el tiempo en rango usa la proporcion de lecturas fuera de rango del intervalo`() {
        val useCase = CalculateTemperatureStatsUseCase()
        // Primer intervalo: mitad de las lecturas fuera de rango. Segundo: todo en rango.
        val buckets = listOf(
            bucket(0, 25.0f, max = 31.0f, samples = 10, outOfRange = 5),
            bucket(1, 22.0f, samples = 10)
        )

        val stats = useCase.fromBuckets(buckets, bucketMs = bucketMs, rangeEnd = 2 * bucketMs)!!

        assertEquals(75f, stats.timeInSafeRangePercent, delta)
    }

    @Test
    fun `los intervalos sin lecturas no cuentan como tiempo vigilado`() {
        val useCase = CalculateTemperatureStatsUseCase()
        // Intervalo seguro y, diez intervalos después, uno totalmente fuera de rango.
        val buckets = listOf(
            bucket(0, 22.0f),
            bucket(10, 31.0f, samples = 12, outOfRange = 12)
        )

        val stats = useCase.fromBuckets(buckets, bucketMs = bucketMs, rangeEnd = 11 * bucketMs)!!

        assertEquals(2 * bucketMs, stats.monitoredDurationMs)
        assertEquals(50f, stats.timeInSafeRangePercent, delta)
    }

    @Test
    fun `el ultimo intervalo solo pesa hasta el final del rango`() {
        val useCase = CalculateTemperatureStatsUseCase()
        val buckets = listOf(bucket(0, 22.0f), bucket(1, 30.0f))

        // El segundo intervalo va por la mitad: pesa la mitad que el primero.
        val stats = useCase.fromBuckets(buckets, bucketMs = bucketMs, rangeEnd = 3 * minute)!!

        assertEquals(3 * minute, stats.monitoredDurationMs)
        assertEquals(24.67f, stats.averageCelsius, delta)
    }

    @Test
    fun `sin lecturas devuelve null para mostrar el estado vacio`() {
        val useCase = CalculateTemperatureStatsUseCase()
        assertNull(useCase(emptyList(), rangeEnd = 0L))
    }

    @Test
    fun `insulina estable a 22_2 grados esta 100 por ciento en rango`() {
        val useCase = CalculateTemperatureStatsUseCase(maxSampleGapMs = 60 * minute)
        val readings = listOf(reading(22.2f, 0), reading(22.2f, 10), reading(22.2f, 20))
        val stats = useCase(readings, rangeEnd = 30 * minute)!!

        assertEquals(100f, stats.timeInSafeRangePercent, delta)
        assertEquals(22.2f, stats.averageCelsius, delta)
        assertEquals(22.2f, stats.minCelsius, delta)
        assertEquals(22.2f, stats.maxCelsius, delta)
    }

    @Test
    fun `el porcentaje se pondera por tiempo y no por cantidad de muestras`() {
        val useCase = CalculateTemperatureStatsUseCase(maxSampleGapMs = 60 * minute)
        // 50 min seguros (una sola muestra) + 10 min críticos (diez muestras).
        val readings = listOf(reading(22.0f, 0)) +
                (50L until 60L).map { reading(31.0f, it) }

        val stats = useCase(readings, rangeEnd = 60 * minute)!!

        // Por muestras serían 1/11 ≈ 9 %; por tiempo, 50/60 ≈ 83.3 %.
        assertEquals(83.33f, stats.timeInSafeRangePercent, delta)
        assertEquals(11, stats.sampleCount)
    }

    @Test
    fun `los huecos sin vigilancia no cuentan como tiempo seguro`() {
        val useCase = CalculateTemperatureStatsUseCase(maxSampleGapMs = 5 * minute)
        // Lectura segura y 2 horas después una crítica: el hueco se limita a 5 min.
        val readings = listOf(reading(22.0f, 0), reading(31.0f, 120))

        val stats = useCase(readings, rangeEnd = 125 * minute)!!

        assertEquals(50f, stats.timeInSafeRangePercent, delta)
        assertEquals(10 * minute, stats.monitoredDurationMs)
    }

    @Test
    fun `minimo maximo y media ponderada con lecturas desordenadas`() {
        val useCase = CalculateTemperatureStatsUseCase(maxSampleGapMs = 60 * minute)
        val readings = listOf(reading(22.0f, 20), reading(22.0f, 0), reading(31.0f, 10))

        val stats = useCase(readings, rangeEnd = 30 * minute)!!

        assertEquals(22.0f, stats.minCelsius, delta)
        assertEquals(31.0f, stats.maxCelsius, delta)
        assertEquals(25.0f, stats.averageCelsius, delta)
        assertEquals(66.67f, stats.timeInSafeRangePercent, delta)
    }
}