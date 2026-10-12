package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.geo.distanceMeters
import com.example.medicaseclinico.domain.model.Geofence
import com.example.medicaseclinico.domain.model.GeofencePresence

/**
 * Sitúa una posición del estuche respecto a las zonas de seguridad activas,
 * de la más cercana a la más lejana, para pintarlas en el mapa y resumir la
 * primera en la tarjeta "Geocerca de Seguridad".
 *
 * Es solo para mostrar: usa el radio tal cual, sin la histéresis de
 * [EvaluateGeofencesUseCase], que es quien decide las alertas. Justo en el
 * borde, lo que se ve aquí puede adelantarse unos metros a la alerta.
 */
class LocateCaseInGeofencesUseCase {

    operator fun invoke(
        geofences: List<Geofence>,
        latitude: Double,
        longitude: Double
    ): List<GeofencePresence> =
        geofences
            .filter { it.isActive }
            .map { geofence ->
                val distanceToEdge = distanceMeters(
                    fromLatitude = geofence.latitude,
                    fromLongitude = geofence.longitude,
                    toLatitude = latitude,
                    toLongitude = longitude
                ) - geofence.radiusMeters
                GeofencePresence(
                    geofence = geofence,
                    isInside = distanceToEdge <= 0.0,
                    distanceToEdgeMeters = distanceToEdge
                )
            }
            .sortedBy { it.distanceToEdgeMeters }
}
