package com.example.medicaseclinico.domain.model

/**
 * Lo que la app sabe **en vivo** de la ubicación del estuche a través del
 * enlace BLE.
 *
 * Distingue por qué no hay posición, porque cada caso se explica distinto al
 * usuario: sin enlace se muestra la última ubicación guardada, sin señal se
 * avisa "Sin señal GPS" y un estuche sin GPS no debe prometer un mapa.
 */
sealed interface CaseLocationState {

    /**
     * No hay enlace con el estuche, o acaba de conectarse y aún no ha enviado
     * ninguna trama de ubicación. No dice nada sobre dónde está el estuche.
     */
    data object Unavailable : CaseLocationState

    /** El estuche conectado no ofrece el servicio de ubicación (firmware sin GPS). */
    data object NotSupported : CaseLocationState

    /**
     * Última trama de ubicación recibida en esta conexión.
     *
     * @param reading posición medida o "sin señal", tal como la informó el GPS.
     * @param receivedAt momento de recepción en el teléfono (epoch ms).
     */
    data class Reported(
        val reading: CaseGpsReading,
        val receivedAt: Long
    ) : CaseLocationState
}
