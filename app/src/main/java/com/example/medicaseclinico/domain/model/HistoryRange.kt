package com.example.medicaseclinico.domain.model

import java.util.concurrent.TimeUnit

/**
 * Ventanas de tiempo del Historial Térmico: las tres pestañas del diseño
 * "Últimas 24h · 7 Días · 30 Días".
 */
enum class HistoryRange(val durationMs: Long) {
    LAST_24_HOURS(TimeUnit.HOURS.toMillis(24)),
    LAST_7_DAYS(TimeUnit.DAYS.toMillis(7)),
    LAST_30_DAYS(TimeUnit.DAYS.toMillis(30));

    /** Inicio de la ventana (epoch ms) relativo a [now]. */
    fun startAt(now: Long): Long = now - durationMs

    companion object {
        /** Pestaña seleccionada al abrir la pantalla, como en el diseño. */
        val DEFAULT = LAST_24_HOURS
    }
}