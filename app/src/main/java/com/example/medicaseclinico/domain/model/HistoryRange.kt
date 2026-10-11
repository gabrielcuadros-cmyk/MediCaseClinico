package com.example.medicaseclinico.domain.model

import java.util.concurrent.TimeUnit

/**
 * Ventanas de tiempo del Historial Térmico: las tres pestañas del diseño
 * "Últimas 24h · 7 Días · 30 Días".
 *
 * @param bucketMs tamaño del intervalo en que se resumen las lecturas del
 * rango. Está elegido para que ningún rango supere unos 720 puntos: suficiente
 * detalle para la gráfica sin cargar cada lectura (30 días a una lectura cada
 * 10 s serían más de 250 000).
 */
enum class HistoryRange(val durationMs: Long, val bucketMs: Long) {
    LAST_24_HOURS(
        durationMs = TimeUnit.HOURS.toMillis(24),
        bucketMs = TimeUnit.MINUTES.toMillis(2)
    ),
    LAST_7_DAYS(
        durationMs = TimeUnit.DAYS.toMillis(7),
        bucketMs = TimeUnit.MINUTES.toMillis(15)
    ),
    LAST_30_DAYS(
        durationMs = TimeUnit.DAYS.toMillis(30),
        bucketMs = TimeUnit.HOURS.toMillis(1)
    );

    /** Inicio de la ventana (epoch ms) relativo a [now]. */
    fun startAt(now: Long): Long = now - durationMs

    companion object {
        /** Pestaña seleccionada al abrir la pantalla, como en el diseño. */
        val DEFAULT = LAST_24_HOURS
    }
}
