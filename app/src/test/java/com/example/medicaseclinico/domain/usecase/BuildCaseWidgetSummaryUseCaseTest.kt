package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.CaseWidgetSummary
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildCaseWidgetSummaryUseCaseTest {

    private val useCase = BuildCaseWidgetSummaryUseCase()
    private val now = 10 * DAY_MS

    private fun reading(celsius: Float, msAgo: Long = 0L) = TemperatureReading(
        temperatureCelsius = celsius,
        timestamp = now - msAgo,
        isAlertTriggered = TemperatureStatus.fromCelsius(celsius).isAlert
    )

    private fun build(
        latest: TemperatureReading?,
        connection: ConnectionStatus = ConnectionStatus.CONNECTED,
        unread: Int = 0
    ) = useCase(latestReading = latest, connection = connection, unreadAlerts = unread, now = now)

    @Test
    fun `sin lecturas no inventa una temperatura`() {
        val summary = build(latest = null, connection = ConnectionStatus.DISCONNECTED, unread = 2)

        assertEquals(
            CaseWidgetSummary.NoReadings(connection = ConnectionStatus.DISCONNECTED, unreadAlerts = 2),
            summary
        )
    }

    @Test
    fun `la lectura se clasifica con el rango seguro de la app`() {
        val safe = build(latest = reading(22.4f)) as CaseWidgetSummary.Reading
        val critical = build(latest = reading(31.0f)) as CaseWidgetSummary.Reading

        assertEquals(TemperatureStatus.SAFE, safe.status)
        assertEquals(22.4f, safe.celsius)
        assertEquals(now, safe.timestamp)
        assertEquals(TemperatureStatus.CRITICAL, critical.status)
    }

    @Test
    fun `con enlace una lectura antigua sigue vigente`() {
        val summary = build(latest = reading(22.0f, msAgo = 2 * DAY_MS)) as CaseWidgetSummary.Reading

        assertFalse(summary.isOutdated)
    }

    @Test
    fun `sin enlace la lectura de justo un dia aun no esta desactualizada`() {
        val summary = build(
            latest = reading(22.0f, msAgo = DAY_MS),
            connection = ConnectionStatus.DISCONNECTED
        ) as CaseWidgetSummary.Reading

        assertFalse(summary.isOutdated)
    }

    @Test
    fun `sin enlace la lectura de mas de un dia queda desactualizada`() {
        val summary = build(
            latest = reading(31.0f, msAgo = DAY_MS + 1),
            connection = ConnectionStatus.CONNECTING
        ) as CaseWidgetSummary.Reading

        assertTrue(summary.isOutdated)
    }

    @Test
    fun `un contador negativo de alertas se muestra como cero`() {
        assertEquals(0, build(latest = reading(22.0f), unread = -3).unreadAlerts)
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1_000L
    }
}
