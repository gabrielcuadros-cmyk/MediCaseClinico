package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.geo.distanceMeters
import com.example.medicaseclinico.domain.model.Geofence
import com.example.medicaseclinico.domain.model.GeofencePresence

/**
 * Sitúa una posición del estuche respecto a las zonas de seguridad activas,
 * para pintarlas en el mapa y resumir la primera en la tarjeta "Geocerca de
 * Seguridad".
 *
 * Orden: primero las zonas que contienen al estuche, de la más pequeña a la
 * más grande, porque la pequeña es la que mejor dice dónde está ("Casa" antes
 * que un barrio entero que también la contiene); después las demás, de la de
 * borde más cercano a la más lejana.
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
            .sortedWith(
                compareByDescending<GeofencePresence> { it.isInside }
                    .thenBy { if (it.isInside) it.geofence.radiusMeters else it.distanceToEdgeMeters }
            )
}
