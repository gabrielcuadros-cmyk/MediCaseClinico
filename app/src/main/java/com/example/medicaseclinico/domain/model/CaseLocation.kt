package com.example.medicaseclinico.domain.model

/**
 * Posición del estuche guardada en el teléfono.
 *
 * Lleva siempre su hora: fuera del alcance BLE es la "última ubicación
 * conocida", y sin la hora el usuario no sabría si es de hace un minuto o de
 * ayer.
 *
 * @param timestamp momento en que el teléfono recibió la posición (epoch ms).
 */
data class CaseLocation(
    val id: Long = 0L,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
