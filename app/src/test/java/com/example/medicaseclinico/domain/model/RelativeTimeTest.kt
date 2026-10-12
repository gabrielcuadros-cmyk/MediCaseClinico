package com.example.medicaseclinico.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    private fun elapsed(ms: Long) = RelativeTime.between(timestamp = now - ms, now = now)

    @Test
    fun `menos de un minuto es ahora`() {
        assertEquals(RelativeTime.JustNow, elapsed(0))
        assertEquals(RelativeTime.JustNow, elapsed(minute - 1))
    }

    @Test
    fun `desde un minuto se cuenta en minutos`() {
        assertEquals(RelativeTime.Minutes(1), elapsed(minute))
        assertEquals(RelativeTime.Minutes(5), elapsed(5 * minute + 30_000))
        assertEquals(RelativeTime.Minutes(59), elapsed(hour - 1))
    }

    @Test
    fun `desde una hora se cuenta en horas redondeando hacia abajo`() {
        assertEquals(RelativeTime.Hours(1), elapsed(hour))
        assertEquals(RelativeTime.Hours(1), elapsed(2 * hour - 1))
        assertEquals(RelativeTime.Hours(23), elapsed(day - 1))
    }

    @Test
    fun `desde un dia se cuenta en dias`() {
        assertEquals(RelativeTime.Days(1), elapsed(day))
        assertEquals(RelativeTime.Days(30), elapsed(30 * day + hour))
    }

    @Test
    fun `una alerta con hora futura es ahora y no un tiempo negativo`() {
        assertEquals(RelativeTime.JustNow, elapsed(-5 * minute))
    }
}
