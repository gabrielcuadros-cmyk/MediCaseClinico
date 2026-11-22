package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.AlertEvent
import com.example.medicaseclinico.domain.model.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectRecentAlertsUseCaseTest {

    private fun alert(id: Long, timestamp: Long, isRead: Boolean = true) = AlertEvent(
        id = id,
        type = AlertType.TEMPERATURE,
        timestamp = timestamp,
        value = 31.0f,
        isRead = isRead
    )

    @Test
    fun `sin alertas la lista esta vacia y el contador en cero`() {
        val summary = SelectRecentAlertsUseCase()(emptyList())

        assertTrue(summary.alerts.isEmpty())
        assertEquals(0, summary.unreadCount)
    }

    @Test
    fun `las alertas salen de la mas reciente a la mas antigua`() {
        val events = listOf(alert(1, 100), alert(2, 300), alert(3, 200))

        val summary = SelectRecentAlertsUseCase()(events)

        assertEquals(listOf(2L, 3L, 1L), summary.alerts.map { it.id })
    }

    @Test
    fun `solo se muestran las que caben y se quedan las mas recientes`() {
        val events = (1L..5L).map { alert(id = it, timestamp = it * 10) }

        val summary = SelectRecentAlertsUseCase(limit = 2)(events)

        assertEquals(listOf(5L, 4L), summary.alerts.map { it.id })
    }

    @Test
    fun `el contador incluye las alertas sin ver que no entran en la lista`() {
        val events = listOf(
            alert(1, 10, isRead = false),
            alert(2, 20, isRead = false),
            alert(3, 30, isRead = true)
        )

        val summary = SelectRecentAlertsUseCase(limit = 1)(events)

        assertEquals(1, summary.alerts.size)
        assertEquals(2, summary.unreadCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un limite de cero no es valido`() {
        SelectRecentAlertsUseCase(limit = 0)
    }
}
