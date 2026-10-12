package com.example.medicaseclinico.domain.model

/**
 * Dónde está el estuche respecto a una zona de seguridad, para mostrarlo.
 *
 * @param isInside true si el estuche está dentro del radio de la zona.
 * @param distanceToEdgeMeters metros hasta el borde: negativo dentro de la
 * zona, positivo fuera. Sirve para saber cuál es la zona más cercana.
 */
data class GeofencePresence(
    val geofence: Geofence,
    val isInside: Boolean,
    val distanceToEdgeMeters: Double
)
