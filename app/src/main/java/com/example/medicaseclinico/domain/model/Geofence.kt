package com.example.medicaseclinico.domain.model

/**
 * Zona de seguridad circular: si el estuche sale de ella, la app alerta.
 *
 * @param id 0 para una zona aún no guardada.
 * @param latitude centro de la zona, grados decimales.
 * @param radiusMeters radio de la zona en metros.
 * @param isActive false si se conserva pero no debe generar alertas.
 */
data class Geofence(
    val id: Long = 0L,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val isActive: Boolean = true
)
