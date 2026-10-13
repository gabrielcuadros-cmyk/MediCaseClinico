package com.example.medicaseclinico.domain.user

import java.time.Instant
import java.time.ZoneId

/**
 * Qué tan reciente fue un acceso, por día de calendario. Decide si la lista
 * dice "Hoy", "Ayer" o la fecha, como en el diseño.
 */
enum class LastAccessDay { TODAY, YESTERDAY, EARLIER }

/**
 * Clasifica un acceso respecto a "ahora".
 *
 * Compara días de calendario y no horas transcurridas: un acceso de anoche a
 * las 23:00 es "Ayer" aunque hayan pasado dos horas. Recibe la zona horaria
 * para poder probarse con una zona fija.
 *
 * Un acceso con fecha futura (reloj del teléfono mal puesto) se trata como de
 * hoy: es más honesto que inventarle un "mañana".
 */
fun lastAccessDayOf(accessAt: Long, now: Long, zone: ZoneId): LastAccessDay {
    val accessDate = Instant.ofEpochMilli(accessAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when {
        !accessDate.isBefore(today) -> LastAccessDay.TODAY
        accessDate == today.minusDays(1) -> LastAccessDay.YESTERDAY
        else -> LastAccessDay.EARLIER
    }
}
