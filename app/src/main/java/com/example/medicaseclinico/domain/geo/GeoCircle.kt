package com.example.medicaseclinico.domain.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Punto sobre la superficie terrestre, en grados decimales. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Contorno de un círculo de [radiusMeters] alrededor de un centro, como
 * polígono cerrado (el último punto repite el primero).
 *
 * Los mapas no dibujan círculos en metros: un radio fijo en píxeles cambiaría
 * de tamaño real con el zoom. Se dibuja este polígono, cuyos vértices están
 * todos a la misma distancia real del centro, con la misma esfera que
 * [distanceMeters] para que lo dibujado coincida con lo que se evalúa.
 *
 * @param vertexCount vértices del polígono; con 64 no se distingue de un círculo.
 */
fun circleOutline(
    centerLatitude: Double,
    centerLongitude: Double,
    radiusMeters: Double,
    vertexCount: Int = 64
): List<GeoPoint> {
    require(vertexCount >= 3) { "Un polígono necesita al menos 3 vértices" }

    val centerLatRad = Math.toRadians(centerLatitude)
    val centerLngRad = Math.toRadians(centerLongitude)
    val angularDistance = radiusMeters / EARTH_RADIUS_METERS

    val vertices = (0 until vertexCount).map { index ->
        val bearing = 2 * PI * index / vertexCount
        val latRad = asin(
            sin(centerLatRad) * cos(angularDistance) +
                cos(centerLatRad) * sin(angularDistance) * cos(bearing)
        )
        val lngRad = centerLngRad + atan2(
            sin(bearing) * sin(angularDistance) * cos(centerLatRad),
            cos(angularDistance) - sin(centerLatRad) * sin(latRad)
        )
        GeoPoint(latitude = Math.toDegrees(latRad), longitude = Math.toDegrees(lngRad))
    }
    return vertices + vertices.first()
}
