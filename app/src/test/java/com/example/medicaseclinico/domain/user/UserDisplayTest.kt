package com.example.medicaseclinico.domain.user

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class UserDisplayTest {

    private val lima = ZoneId.of("America/Lima")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(lima).toInstant().toEpochMilli()

    @Test
    fun `las iniciales son la primera letra de las dos primeras palabras`() {
        assertEquals("SM", initialsOf("Sofía Martínez"))
        assertEquals("DL", initialsOf("Dr. Luis Ramos"))
    }

    @Test
    fun `las iniciales ignoran espacios de mas y van en mayuscula`() {
        assertEquals("AD", initialsOf("  alejandro   díaz "))
        assertEquals("L", initialsOf("laura"))
    }

    @Test
    fun `sin nombre no hay iniciales`() {
        assertEquals("", initialsOf("   "))
    }

    @Test
    fun `un acceso del mismo dia es de hoy`() {
        val now = at(2026, 10, 12, 20, 0)

        assertEquals(LastAccessDay.TODAY, lastAccessDayOf(at(2026, 10, 12, 8, 15), now, lima))
    }

    @Test
    fun `un acceso de anoche es de ayer aunque hayan pasado pocas horas`() {
        val now = at(2026, 10, 12, 1, 0)

        assertEquals(LastAccessDay.YESTERDAY, lastAccessDayOf(at(2026, 10, 11, 23, 0), now, lima))
    }

    @Test
    fun `un acceso de hace dos dias o mas es anterior`() {
        val now = at(2026, 10, 12, 9, 0)

        assertEquals(LastAccessDay.EARLIER, lastAccessDayOf(at(2026, 10, 10, 9, 0), now, lima))
    }

    @Test
    fun `el dia se decide en la zona horaria indicada y no en utc`() {
        // 12 oct 21:00 en Lima ya es 13 oct 02:00 en UTC.
        val access = at(2026, 10, 12, 21, 0)
        val now = at(2026, 10, 12, 23, 0)

        assertEquals(LastAccessDay.TODAY, lastAccessDayOf(access, now, lima))
        assertEquals(LastAccessDay.TODAY, lastAccessDayOf(access, now, ZoneId.of("UTC")))
        assertEquals(
            LastAccessDay.YESTERDAY,
            lastAccessDayOf(at(2026, 10, 12, 18, 0), at(2026, 10, 12, 20, 0), ZoneId.of("UTC"))
        )
    }

    @Test
    fun `un acceso con fecha futura se trata como de hoy`() {
        val now = at(2026, 10, 12, 9, 0)

        assertEquals(LastAccessDay.TODAY, lastAccessDayOf(at(2026, 10, 13, 9, 0), now, lima))
    }
}
