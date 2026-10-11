package com.example.medicaseclinico.ui.screens.history.components

import com.example.medicaseclinico.domain.model.TemperatureBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class StabilityChartModelTest {

    private val delta = 0.001f
    private val bucketMs = TimeUnit.MINUTES.toMillis(2)
    private val rangeEnd = 10 * bucketMs

    private fun bucket(index: Long, average: Float, min: Float = average, max: Float = average) =
        TemperatureBucket(
            startTimestamp = index * bucketMs,
            averageCelsius = average,
            minCelsius = min,
            maxCelsius = max,
            sampleCount = 12,
            outOfRangeCount = 0
        )

    private fun model(vararg buckets: TemperatureBucket) = buildStabilityChartModel(
        buckets = buckets.toList(),
        bucketMs = bucketMs,
        rangeStart = 0L,
        rangeEnd = rangeEnd
    )

    @Test
    fun `sin lecturas no hay linea y la escala muestra la banda segura con margen`() {
        val model = model()

        assertEquals(0, model.pointCount)
        assertEquals(0f, model.minCelsius, delta)
        assertEquals(10f, model.maxCelsius, delta)
    }

    @Test
    fun `intervalos seguidos forman un solo tramo ordenado en el tiempo`() {
        val model = model(bucket(2, 4.1f), bucket(0, 4.2f), bucket(1, 4.0f))

        val points = model.segments.single()
        assertEquals(listOf(4.2f, 4.0f, 4.1f), points.map { it.celsius })
        assertEquals(0.05f, points.first().xFraction, delta)
        assertTrue(points.zipWithNext().all { (a, b) -> a.xFraction < b.xFraction })
    }

    @Test
    fun `un hueco sin lecturas parte la linea en dos tramos`() {
        val model = model(bucket(0, 4.0f), bucket(1, 4.0f), bucket(5, 4.0f))

        assertEquals(listOf(2, 1), model.segments.map { it.size })
    }

    @Test
    fun `un pico amplia la escala y se marca como fuera de rango`() {
        val model = model(bucket(0, 4.0f), bucket(1, 6.0f, min = 4.0f, max = 11.0f))

        assertEquals(11.5f, model.maxCelsius, delta)
        val excursion = model.excursions.single()
        assertEquals(11.0f, excursion.maxCelsius, delta)
        assertTrue(model.yFraction(11.0f) > 0f)
    }

    @Test
    fun `los limites de la banda quedan dentro de la grafica`() {
        val model = model(bucket(0, 4.0f))

        assertEquals(0.2f, model.yFraction(8f), delta)
        assertEquals(0.8f, model.yFraction(2f), delta)
        assertTrue(model.excursions.isEmpty())
    }
}
