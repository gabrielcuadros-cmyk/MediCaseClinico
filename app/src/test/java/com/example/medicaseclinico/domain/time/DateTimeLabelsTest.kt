package com.example.medicaseclinico.domain.time

import com.example.medicaseclinico.domain.model.HistoryRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class DateTimeLabelsTest {

    // Zona fija y distinta de UTC: en Lima son 5 horas menos todo el año.
    private val lima = ZoneId.of("America/Lima")

    // Idioma fijo para que los nombres de los meses no dependan del equipo que ejecuta el test.
    private val labels = DateTimeLabels(zone = lima, locale = Locale.ENGLISH)

    /** Un momento dado por su hora en UTC, como lo guarda la base de datos. */
    private fun utc(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `la hora de la lectura se escribe en la zona indicada y no en utc`() {
        // 23:04 UTC del 12 de octubre son las 18:04 en Lima.
        assertEquals("12 Oct, 18:04", labels.readingTime(utc(day = 12, hour = 23, minute = 4)))
    }

    @Test
    fun `pasada la medianoche utc en lima sigue siendo el dia anterior`() {
        // 02:30 UTC del 13 de octubre son las 21:30 del 12 en Lima.
        val timestamp = utc(day = 13, hour = 2, minute = 30)

        assertEquals("12 Oct, 21:30", labels.readingTime(timestamp))
        assertEquals("12 Oct 2026, 21:30", labels.reportDateTime(timestamp))
        assertEquals("20261012-213000", labels.fileStamp(timestamp))
    }

    @Test
    fun `la misma lectura en otra zona da otra hora`() {
        val timestamp = utc(day = 13, hour = 2, minute = 30)
        val utcLabels = DateTimeLabels(zone = ZoneOffset.UTC, locale = Locale.ENGLISH)

        assertEquals("13 Oct, 02:30", utcLabels.readingTime(timestamp))
    }

    @Test
    fun `el eje de 24 horas marca horas repartidas por igual en la zona indicada`() {
        // De las 19:00 del 11 a las 19:00 del 12, hora de Lima.
        val end = utc(day = 13, hour = 0)
        val start = end - HistoryRange.LAST_24_HOURS.durationMs

        assertEquals(
            listOf("19:00", "01:00", "07:00", "13:00", "19:00"),
            labels.axisLabels(HistoryRange.LAST_24_HOURS, start, end, count = 5)
        )
    }

    @Test
    fun `el eje de varios dias marca fechas`() {
        val end = utc(day = 13, hour = 0)
        val start = end - HistoryRange.LAST_7_DAYS.durationMs

        val axis = labels.axisLabels(HistoryRange.LAST_7_DAYS, start, end, count = 5)

        assertEquals("5 Oct", axis.first())
        assertEquals("12 Oct", axis.last())
        assertEquals(5, axis.size)
    }

    @Test
    fun `sin marcas que dibujar el eje esta vacio`() {
        assertTrue(labels.axisLabels(HistoryRange.LAST_24_HOURS, 0L, 1_000L, count = 0).isEmpty())
    }

    @Test
    fun `un evento del mismo dia en lima es de hoy`() {
        // Referencia: 20:00 del 12 en Lima. Evento: 08:15 del 12 en Lima.
        val reference = utc(day = 13, hour = 1)
        val event = utc(day = 12, hour = 13, minute = 15)

        val label = labels.eventTime(timestamp = event, referenceTime = reference)

        assertTrue(label is EventTimeLabel.Today)
        assertTrue(label.time.contains("8:15"))
    }

    @Test
    fun `un evento de anoche es de ayer aunque en utc sea el mismo dia`() {
        // Referencia: 01:00 del 13 en Lima (06:00 UTC del 13).
        // Evento: 23:00 del 12 en Lima (04:00 UTC del 13): mismo día en UTC, ayer en Lima.
        val reference = utc(day = 13, hour = 6)
        val event = utc(day = 13, hour = 4)

        val label = labels.eventTime(timestamp = event, referenceTime = reference)

        assertTrue(label is EventTimeLabel.Yesterday)
        assertTrue(label.time.contains("11:00"))
    }

    @Test
    fun `un evento mas antiguo lleva su fecha`() {
        val reference = utc(day = 13, hour = 1)
        val event = utc(day = 6, hour = 3, minute = 11)

        val label = labels.eventTime(timestamp = event, referenceTime = reference)

        // 03:11 UTC del 6 son las 22:11 del 5 en Lima.
        assertEquals("5 Oct", (label as EventTimeLabel.Earlier).date)
        assertTrue(label.time.contains("10:11"))
    }
}
