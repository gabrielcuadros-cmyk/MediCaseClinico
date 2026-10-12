package com.example.medicaseclinico.domain.model

/**
 * Lo que el GPS del estuche informa en una trama de ubicación.
 *
 * "Sin señal" es un valor propio y no un null ni unas coordenadas en cero:
 * la app debe poder decir "Sin señal GPS" y nunca dibujar una posición que
 * el estuche no ha medido.
 */
sealed interface CaseGpsReading {

    /**
     * Posición medida por el GPS del estuche.
     *
     * @param latitude grados decimales, de -90 (sur) a 90 (norte).
     * @param longitude grados decimales, de -180 (oeste) a 180 (este).
     */
    data class Fix(val latitude: Double, val longitude: Double) : CaseGpsReading

    /** El GPS está encendido pero aún no tiene posición (interiores, arranque en frío). */
    data object NoSignal : CaseGpsReading
}
