package com.example.medicaseclinico.domain.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Radio medio de la Tierra en metros (esfera de referencia de la fórmula). */
private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Distancia en metros entre dos puntos sobre la superficie terrestre
 * (fórmula del haversine).
 *
 * Vive en el dominio, sin Android, porque la usan el muestreo de posiciones
 * y la evaluación de geocercas, y ambos se prueban con tests JVM. Trata la
 * Tierra como una esfera: el error es menor al 0.5 %, despreciable frente a
 * la precisión de unos metros del GPS del estuche.
 */
fun distanceMeters(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): Double {
    val fromLatRad = Math.toRadians(fromLatitude)
    val toLatRad = Math.toRadians(toLatitude)
    val deltaLat = toLatRad - fromLatRad
    val deltaLng = Math.toRadians(toLongitude - fromLongitude)

    val haversine = sin(deltaLat / 2).pow(2) +
        cos(fromLatRad) * cos(toLatRad) * sin(deltaLng / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}
