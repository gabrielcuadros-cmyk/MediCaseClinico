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

/**
 * Límites de fábrica de una zona de seguridad.
 *
 * El radio mínimo queda por encima de la histéresis de las alertas y del
 * vaivén de un GPS quieto: una zona más pequeña alertaría sin que el estuche
 * se mueva.
 */
object GeofenceLimits {
    const val MIN_RADIUS_METERS = 50
    const val MAX_RADIUS_METERS = 1_000
    const val RADIUS_STEP_METERS = 50
    const val DEFAULT_RADIUS_METERS = 300
    const val MAX_NAME_LENGTH = 40
}
