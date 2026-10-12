package com.example.medicaseclinico.domain.alert

import com.example.medicaseclinico.domain.model.AlertEvent
import com.example.medicaseclinico.domain.model.AlertType
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.repository.AlertEventRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingAlertPublisherTest {

    private val delta = 0.01f

    private class FakeAlertEventRepository(private val failing: Boolean = false) : AlertEventRepository {
        val recorded = mutableListOf<AlertEvent>()

        override fun observeAlertEvents(): Flow<List<AlertEvent>> = flowOf(recorded.toList())
        override fun observeUnreadCount(): Flow<Int> = flowOf(recorded.count { !it.isRead })
        override suspend fun markAllAsRead(): Result<Unit> = Result.success(Unit)

        override suspend fun recordAlert(event: AlertEvent): Result<Long> {
            if (failing) return Result.failure(IOException("disco lleno"))
            recorded += event
            return Result.success(recorded.size.toLong())
        }
    }

    private class FakeAlertPublisher : AlertPublisher {
        val calls = mutableListOf<String>()

        override fun onTemperature(celsius: Float, status: TemperatureStatus) {
            calls += "temperature"
        }
        override fun onBatteryLevel(percent: Int) {
            calls += "battery"
        }
        override fun onConnectionLost() {
            calls += "lost"
        }
        override fun onConnectionRestored() {
            calls += "restored"
        }
    }

    private class Harness(scope: TestScope, failing: Boolean = false) {
        var now = 1_000L
        val repository = FakeAlertEventRepository(failing)
        val delegate = FakeAlertPublisher()
        val publisher = RecordingAlertPublisher(
            delegate = delegate,
            alertEventRepository = repository,
            scope = scope,
            clock = { now }
        )

        fun temperature(celsius: Float) =
            publisher.onTemperature(celsius, TemperatureStatus.fromCelsius(celsius))
    }

    private fun alertTest(failing: Boolean = false, body: Harness.() -> Unit) =
        runTest(UnconfinedTestDispatcher()) { Harness(this, failing).body() }

    @Test
    fun `temperatura fuera de rango registra una sola alerta por episodio`() = alertTest {
        temperature(8.4f)
        temperature(9.1f)
        temperature(8.7f)

        val event = repository.recorded.single()
        assertEquals(AlertType.TEMPERATURE, event.type)
        assertEquals(8.4f, event.value!!, delta)
        assertEquals(1_000L, event.timestamp)
    }

    @Test
    fun `volver al rango y salir de nuevo registra una segunda alerta`() = alertTest {
        temperature(8.4f)
        temperature(5.0f)
        now = 2_000L
        temperature(1.2f)

        assertEquals(listOf(8.4f, 1.2f), repository.recorded.map { it.value })
        assertEquals(listOf(1_000L, 2_000L), repository.recorded.map { it.timestamp })
    }

    @Test
    fun `temperatura segura o cerca del limite no registra nada`() = alertTest {
        temperature(4.2f)
        temperature(7.8f)
        temperature(2.0f)

        assertTrue(repository.recorded.isEmpty())
    }

    @Test
    fun `bateria baja se registra una vez hasta que se recupera`() = alertTest {
        listOf(30, 19, 17, 22, 18).forEach { publisher.onBatteryLevel(it) }

        val event = repository.recorded.single()
        assertEquals(AlertType.BATTERY, event.type)
        assertEquals(19f, event.value!!, delta)

        publisher.onBatteryLevel(80)
        publisher.onBatteryLevel(10)

        assertEquals(listOf(19f, 10f), repository.recorded.map { it.value })
    }

    @Test
    fun `desconexion sin haber conectado nunca no es una alerta`() = alertTest {
        publisher.onConnectionLost()

        assertTrue(repository.recorded.isEmpty())
    }

    @Test
    fun `cada desconexion tras estar conectado se registra una sola vez`() = alertTest {
        publisher.onConnectionRestored()
        publisher.onConnectionLost()
        publisher.onConnectionLost()

        val event = repository.recorded.single()
        assertEquals(AlertType.CONNECTION, event.type)
        assertNull(event.value)

        publisher.onConnectionRestored()
        publisher.onConnectionLost()

        assertEquals(2, repository.recorded.size)
    }

    @Test
    fun `todas las llamadas llegan al emisor real aunque no se registren`() = alertTest {
        temperature(4.2f)
        temperature(9.0f)
        temperature(9.5f)
        publisher.onBatteryLevel(50)
        publisher.onConnectionRestored()
        publisher.onConnectionLost()

        assertEquals(
            listOf("temperature", "temperature", "temperature", "battery", "restored", "lost"),
            delegate.calls
        )
    }

    @Test
    fun `si falla el guardado la alerta se sigue emitiendo`() = alertTest(failing = true) {
        temperature(9.0f)

        assertTrue(repository.recorded.isEmpty())
        assertEquals(listOf("temperature"), delegate.calls)
    }
}
