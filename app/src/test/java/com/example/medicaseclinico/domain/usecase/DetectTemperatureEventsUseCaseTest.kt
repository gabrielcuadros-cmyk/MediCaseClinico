package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.HistoryEvent
import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureExcursion
import com.example.medicaseclinico.domain.model.TemperatureReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class DetectTemperatureEventsUseCaseTest {

    private val delta = 0.01f
    private val minute = TimeUnit.MINUTES.toMillis(1)
    private val useCase = DetectTemperatureEventsUseCase()

    private fun reading(celsius: Float, atMinute: Long, battery: Int? = null) = TemperatureReading(
        temperatureCelsius = celsius,
        timestamp = atMinute * minute,
        isAlertTriggered = celsius !in 2f..8f,
        batteryLevel = battery
    )

    private val bucketMs = 2 * minute

    private fun bucket(
        index: Long,
        average: Float,
        min: Float = average,
        max: Float = average,
        battery: Int? = null
    ) = TemperatureBucket(
        startTimestamp = index * bucketMs,
        averageCelsius = average,
        minCelsius = min,
        maxCelsius = max,
        sampleCount = 12,
        outOfRangeCount = if (min < 2f || max > 8f) 1 else 0,
        lastBatteryLevel = battery
    )

    @Test
    fun `intervalos estables en rango no generan eventos`() {
        val buckets = listOf(bucket(0, 4.2f, min = 4.0f, max = 4.4f), bucket(1, 4.3f))
        assertTrue(useCase.fromBuckets(buckets).isEmpty())
    }

    @Test
    fun `un pico breve no se pierde aunque la media del intervalo sea normal`() {
        val buckets = listOf(bucket(0, 4.2f), bucket(1, 5.0f, max = 8.4f), bucket(2, 4.2f))

        val events = useCase.fromBuckets(buckets)

        val alert = events.filterIsInstance<HistoryEvent.TemperatureAlert>().single()
        assertEquals(TemperatureExcursion.HIGH, alert.direction)
        assertEquals(8.4f, alert.peakCelsius, delta)
        assertEquals(1 * bucketMs, alert.timestamp)
        val normalized = events.filterIsInstance<HistoryEvent.TemperatureNormalized>().single()
        assertEquals(2 * bucketMs, normalized.timestamp)
        assertEquals(4.2f, normalized.celsius, delta)
    }

    @Test
    fun `intervalos seguidos fuera de rango son una alerta con el maximo de todos`() {
        val buckets = listOf(
            bucket(0, 8.2f, max = 8.5f),
            bucket(1, 8.8f, max = 9.6f),
            bucket(2, 8.1f, max = 8.3f)
        )

        val alert = useCase.fromBuckets(buckets).single() as HistoryEvent.TemperatureAlert

        assertEquals(9.6f, alert.peakCelsius, delta)
        assertEquals(0L, alert.timestamp)
        assertNull(alert.endTimestamp)
    }

    @Test
    fun `temperatura baja en un intervalo usa su minimo como pico`() {
        val buckets = listOf(bucket(0, 2.5f, min = 1.2f), bucket(1, 4.0f))

        val alert = useCase.fromBuckets(buckets)
            .filterIsInstance<HistoryEvent.TemperatureAlert>().single()

        assertEquals(TemperatureExcursion.LOW, alert.direction)
        assertEquals(1.2f, alert.peakCelsius, delta)
    }

    @Test
    fun `un intervalo con maximo alto y minimo bajo genera las dos alertas`() {
        val buckets = listOf(bucket(0, 5.0f, min = 1.0f, max = 9.0f))

        val alerts = useCase.fromBuckets(buckets).filterIsInstance<HistoryEvent.TemperatureAlert>()

        assertEquals(
            setOf(TemperatureExcursion.HIGH, TemperatureExcursion.LOW),
            alerts.map { it.direction }.toSet()
        )
    }

    @Test
    fun `la ultima bateria del intervalo genera el evento de bateria baja una sola vez`() {
        val buckets = listOf(
            bucket(0, 4.0f, battery = 40),
            bucket(1, 4.0f, battery = 18),
            bucket(2, 4.0f, battery = null),
            bucket(3, 4.0f, battery = 16)
        )

        val event = useCase.fromBuckets(buckets).single() as HistoryEvent.BatteryLow

        assertEquals(1 * bucketMs, event.timestamp)
        assertEquals(18, event.levelPercent)
    }

    @Test
    fun `sin lecturas no hay eventos para mostrar el estado vacio`() {
        assertTrue(useCase(emptyList()).isEmpty())
    }

    @Test
    fun `insulina estable en rango no genera eventos`() {
        val readings = listOf(reading(4.2f, 0), reading(4.3f, 1), reading(4.1f, 2))
        assertTrue(useCase(readings).isEmpty())
    }

    @Test
    fun `cerca del limite pero dentro del rango no es una alerta`() {
        val readings = listOf(reading(7.8f, 0), reading(8.0f, 1), reading(2.0f, 2))
        assertTrue(useCase(readings).isEmpty())
    }

    @Test
    fun `varias lecturas altas seguidas son una sola alerta con su pico`() {
        val readings = listOf(
            reading(4.2f, 0),
            reading(8.1f, 1),
            reading(8.4f, 2),
            reading(8.2f, 3),
            reading(6.0f, 4)
        )

        val events = useCase(readings)

        assertEquals(2, events.size)
        val alert = events[1] as HistoryEvent.TemperatureAlert
        assertEquals(TemperatureExcursion.HIGH, alert.direction)
        assertEquals(8.4f, alert.peakCelsius, delta)
        assertEquals(1 * minute, alert.timestamp)
        assertEquals(3 * minute, alert.endTimestamp)
    }

    @Test
    fun `al volver al rango se registra la normalizacion despues de la alerta`() {
        val readings = listOf(reading(8.4f, 0), reading(4.2f, 5))

        val events = useCase(readings)

        val normalized = events.first() as HistoryEvent.TemperatureNormalized
        assertEquals(5 * minute, normalized.timestamp)
        assertEquals(4.2f, normalized.celsius, delta)
        assertEquals(TemperatureExcursion.HIGH, normalized.recoveredFrom)
    }

    @Test
    fun `temperatura baja usa el minimo como pico`() {
        val readings = listOf(reading(1.5f, 0), reading(0.8f, 1), reading(1.9f, 2), reading(2.1f, 3))

        val events = useCase(readings)

        val alert = events.filterIsInstance<HistoryEvent.TemperatureAlert>().single()
        assertEquals(TemperatureExcursion.LOW, alert.direction)
        assertEquals(0.8f, alert.peakCelsius, delta)
        val normalized = events.filterIsInstance<HistoryEvent.TemperatureNormalized>().single()
        assertEquals(TemperatureExcursion.LOW, normalized.recoveredFrom)
    }

    @Test
    fun `alerta aun activa queda abierta y sin normalizacion`() {
        val readings = listOf(reading(4.0f, 0), reading(9.0f, 1), reading(9.5f, 2))

        val events = useCase(readings)

        val alert = events.single() as HistoryEvent.TemperatureAlert
        assertNull(alert.endTimestamp)
        assertEquals(9.5f, alert.peakCelsius, delta)
    }

    @Test
    fun `pasar de alta a baja sin zona segura son dos alertas sin normalizacion`() {
        val readings = listOf(reading(9.0f, 0), reading(1.0f, 1))

        val events = useCase(readings)

        assertEquals(2, events.size)
        assertTrue(events.all { it is HistoryEvent.TemperatureAlert })
        assertEquals(TemperatureExcursion.LOW, (events[0] as HistoryEvent.TemperatureAlert).direction)
        assertEquals(0L, (events[1] as HistoryEvent.TemperatureAlert).endTimestamp)
    }

    @Test
    fun `rebotes en el limite se unen en un solo episodio con una normalizacion`() {
        val readings = listOf(
            reading(8.2f, 0),
            reading(7.9f, 1),
            reading(8.3f, 2),
            reading(7.9f, 3),
            reading(8.6f, 4),
            reading(6.0f, 5),
            reading(6.0f, 20)
        )

        val events = useCase(readings)

        assertEquals(2, events.size)
        val alert = events[1] as HistoryEvent.TemperatureAlert
        assertEquals(0L, alert.timestamp)
        assertEquals(4 * minute, alert.endTimestamp)
        assertEquals(8.6f, alert.peakCelsius, delta)
        val normalized = events[0] as HistoryEvent.TemperatureNormalized
        assertEquals(5 * minute, normalized.timestamp)
    }

    @Test
    fun `el episodio unido conserva el pico mas alto aunque sea del primer tramo`() {
        val readings = listOf(reading(9.5f, 0), reading(6.0f, 1), reading(8.2f, 2), reading(6.0f, 3))

        val alert = useCase(readings).filterIsInstance<HistoryEvent.TemperatureAlert>().single()

        assertEquals(9.5f, alert.peakCelsius, delta)
        assertEquals(2 * minute, alert.endTimestamp)
    }

    @Test
    fun `rebote en temperatura baja conserva el minimo como pico`() {
        val readings = listOf(reading(1.8f, 0), reading(2.2f, 1), reading(1.1f, 3), reading(1.6f, 4))

        val alert = useCase(readings).single() as HistoryEvent.TemperatureAlert

        assertEquals(TemperatureExcursion.LOW, alert.direction)
        assertEquals(1.1f, alert.peakCelsius, delta)
        assertNull(alert.endTimestamp)
    }

    @Test
    fun `alertas separadas por cinco minutos o mas en rango son episodios distintos`() {
        val readings = listOf(reading(8.5f, 0), reading(6.0f, 1), reading(9.0f, 5), reading(6.0f, 6))

        val events = useCase(readings)

        val alerts = events.filterIsInstance<HistoryEvent.TemperatureAlert>()
        assertEquals(listOf(9.0f, 8.5f), alerts.map { it.peakCelsius })
        val normalizations = events.filterIsInstance<HistoryEvent.TemperatureNormalized>()
        assertEquals(listOf(6 * minute, 1 * minute), normalizations.map { it.timestamp })
    }

    @Test
    fun `alta y baja no se unen aunque esten a menos de cinco minutos`() {
        val readings = listOf(reading(9.0f, 0), reading(4.0f, 1), reading(1.0f, 2))

        val events = useCase(readings)

        assertEquals(3, events.size)
        assertEquals(TemperatureExcursion.LOW, (events[0] as HistoryEvent.TemperatureAlert).direction)
        assertEquals(TemperatureExcursion.HIGH, (events[1] as HistoryEvent.TemperatureNormalized).recoveredFrom)
        assertEquals(TemperatureExcursion.HIGH, (events[2] as HistoryEvent.TemperatureAlert).direction)
    }

    @Test
    fun `lecturas desordenadas se procesan en orden cronologico`() {
        val readings = listOf(reading(4.0f, 3), reading(8.5f, 1), reading(4.0f, 0), reading(9.0f, 2))

        val events = useCase(readings)

        val alert = events.filterIsInstance<HistoryEvent.TemperatureAlert>().single()
        assertEquals(1 * minute, alert.timestamp)
        assertEquals(9.0f, alert.peakCelsius, delta)
        assertEquals(2 * minute, alert.endTimestamp)
    }

    @Test
    fun `los eventos salen del mas reciente al mas antiguo`() {
        val readings = listOf(
            reading(9.0f, 0),
            reading(4.0f, 10),
            reading(4.0f, 20, battery = 15),
            reading(1.0f, 30)
        )

        val timestamps = useCase(readings).map { it.timestamp }

        assertEquals(listOf(30 * minute, 20 * minute, 10 * minute, 0L), timestamps)
    }

    @Test
    fun `bateria bajo el umbral genera un solo evento aunque siga baja`() {
        val readings = listOf(
            reading(4.0f, 0, battery = 30),
            reading(4.0f, 1, battery = 19),
            reading(4.0f, 2, battery = 17),
            reading(4.0f, 3, battery = 22),
            reading(4.0f, 4, battery = 18)
        )

        val event = useCase(readings).single() as HistoryEvent.BatteryLow

        assertEquals(1 * minute, event.timestamp)
        assertEquals(19, event.levelPercent)
    }

    @Test
    fun `bateria recargada y de nuevo baja genera un segundo evento`() {
        val readings = listOf(
            reading(4.0f, 0, battery = 15),
            reading(4.0f, 1, battery = 80),
            reading(4.0f, 2, battery = 10)
        )

        val events = useCase(readings).filterIsInstance<HistoryEvent.BatteryLow>()

        assertEquals(listOf(10, 15), events.map { it.levelPercent })
    }

    @Test
    fun `bateria justo en el umbral o sin dato no genera evento`() {
        val readings = listOf(reading(4.0f, 0, battery = 20), reading(4.0f, 1, battery = null))
        assertTrue(useCase(readings).isEmpty())
    }

    @Test
    fun `una lectura no finita se descarta y no aparece como pico`() {
        val readings = listOf(reading(4.0f, 0), reading(Float.NaN, 1), reading(4.0f, 2))
        assertTrue(useCase(readings).isEmpty())
    }
}
