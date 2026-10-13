package com.example.medicaseclinico.domain.time

import com.example.medicaseclinico.domain.model.HistoryRange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Fechas y horas que la app muestra (Historial Térmico, reporte PDF, Mapa y
 * Usuarios Autorizados), en un único lugar.
 *
 * Antes cada pantalla creaba su formateador con la zona del teléfono, así que
 * la gráfica y el PDF podían escribir la misma hora de dos formas y nada de
 * ello se podía probar. Aquí la zona y el idioma se reciben por parámetro: la
 * app pasa los del teléfono ([system]) y los tests, unos fijos.
 *
 * @param zone zona horaria en la que se muestran las horas.
 * @param locale idioma de los nombres de los meses y del formato de la hora.
 */
class DateTimeLabels(
    private val zone: ZoneId,
    private val locale: Locale
) {
    private val readingTime = formatter(READING_TIME_PATTERN)
    private val reportDateTime = formatter(REPORT_DATE_TIME_PATTERN)
    private val axisHour = formatter(AXIS_HOUR_PATTERN)
    private val axisDay = formatter(AXIS_DAY_PATTERN)
    private val eventDate = formatter(EVENT_DATE_PATTERN)
    private val positionTime = formatter(POSITION_TIME_PATTERN)
    private val accessTime = formatter(ACCESS_TIME_PATTERN)
    private val accessDateTime = formatter(ACCESS_DATE_TIME_PATTERN)
    private val shortTime =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)

    // Va en el nombre de un archivo: siempre con dígitos latinos, sea cual sea el idioma.
    private val fileStamp =
        DateTimeFormatter.ofPattern(FILE_STAMP_PATTERN, Locale.ROOT).withZone(zone)

    /** Momento de la lectura actual: "12 oct, 18:04". */
    fun readingTime(timestamp: Long): String = readingTime.format(Instant.ofEpochMilli(timestamp))

    /** Fecha y hora completas para el reporte: "12 oct 2026, 18:04". */
    fun reportDateTime(timestamp: Long): String =
        reportDateTime.format(Instant.ofEpochMilli(timestamp))

    /** Marca para el nombre del archivo del reporte: "20261012-180400". */
    fun fileStamp(timestamp: Long): String = fileStamp.format(Instant.ofEpochMilli(timestamp))

    /**
     * Marcas del eje de tiempo, repartidas por igual entre [rangeStart] y
     * [rangeEnd]: horas en el rango de 24 h y fechas en los rangos de días.
     */
    fun axisLabels(range: HistoryRange, rangeStart: Long, rangeEnd: Long, count: Int): List<String> {
        if (count <= 0) return emptyList()
        val formatter = if (range == HistoryRange.LAST_24_HOURS) axisHour else axisDay
        val lastIndex = (count - 1).coerceAtLeast(1)
        return List(count) { index ->
            val timestamp = rangeStart + (rangeEnd - rangeStart) * index / lastIndex
            formatter.format(Instant.ofEpochMilli(timestamp))
        }
    }

    /**
     * Cuándo ocurrió un evento respecto a [referenceTime], por día de
     * calendario en la zona indicada: un evento de anoche es de "ayer" aunque
     * hayan pasado pocas horas.
     */
    fun eventTime(timestamp: Long, referenceTime: Long): EventTimeLabel {
        val eventInstant = Instant.ofEpochMilli(timestamp)
        val eventDay = eventInstant.atZone(zone).toLocalDate()
        val referenceDay = Instant.ofEpochMilli(referenceTime).atZone(zone).toLocalDate()
        val time = shortTime.format(eventInstant)
        return when (eventDay) {
            referenceDay -> EventTimeLabel.Today(time = time)
            referenceDay.minusDays(1) -> EventTimeLabel.Yesterday(time = time)
            else -> EventTimeLabel.Earlier(date = eventDate.format(eventInstant), time = time)
        }
    }

    /**
     * Hora de una posición del estuche, con segundos y desfase horario como en
     * el diseño del Mapa: "18:04:09 GMT-5".
     */
    fun positionTime(timestamp: Long): String = positionTime.format(Instant.ofEpochMilli(timestamp))

    /** Hora de un acceso de hoy o de ayer, en formato de 12 horas: "08:15 a. m.". */
    fun accessTime(timestamp: Long): String = accessTime.format(Instant.ofEpochMilli(timestamp))

    /** Fecha y hora de un acceso más antiguo: "12 oct, 11:20 a. m.". */
    fun accessDateTime(timestamp: Long): String =
        accessDateTime.format(Instant.ofEpochMilli(timestamp))

    private fun formatter(pattern: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern, locale).withZone(zone)

    companion object {
        private const val READING_TIME_PATTERN = "d MMM, HH:mm"
        private const val REPORT_DATE_TIME_PATTERN = "d MMM yyyy, HH:mm"
        private const val FILE_STAMP_PATTERN = "yyyyMMdd-HHmmss"
        private const val AXIS_HOUR_PATTERN = "HH:mm"
        private const val AXIS_DAY_PATTERN = "d MMM"
        private const val EVENT_DATE_PATTERN = "d MMM"
        private const val POSITION_TIME_PATTERN = "HH:mm:ss O"
        private const val ACCESS_TIME_PATTERN = "hh:mm a"
        private const val ACCESS_DATE_TIME_PATTERN = "d MMM, hh:mm a"

        /** Con la zona horaria y el idioma del teléfono: lo que usa la app. */
        fun system(): DateTimeLabels =
            DateTimeLabels(zone = ZoneId.systemDefault(), locale = Locale.getDefault())
    }
}

/** Hora de un evento, ya clasificada para que la pantalla le ponga su texto ("Ayer …"). */
sealed interface EventTimeLabel {
    val time: String

    data class Today(override val time: String) : EventTimeLabel

    data class Yesterday(override val time: String) : EventTimeLabel

    /** @param date día y mes del evento: "5 oct". */
    data class Earlier(val date: String, override val time: String) : EventTimeLabel
}
