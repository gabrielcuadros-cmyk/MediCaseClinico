package com.example.medicaseclinico.domain.model

/**
 * Dato recibido del estuche en tiempo real.
 *
 * Temperatura y batería llegan por características BLE distintas y en
 * momentos distintos (el Health Thermometer notifica cada pocos segundos;
 * la batería cambia mucho más despacio), por eso son eventos separados.
 */
sealed interface CaseTelemetry {
    /** Marca de tiempo de recepción en milisegundos epoch. */
    val receivedAt: Long

    /** Medición del Health Thermometer Service (0x1809 / 0x2A1C). */
    data class Temperature(
        val celsius: Float,
        override val receivedAt: Long
    ) : CaseTelemetry

    /** Nivel del Battery Service (0x180F / 0x2A19), validado en 0..100. */
    data class Battery(
        val levelPercent: Int,
        override val receivedAt: Long
    ) : CaseTelemetry
}