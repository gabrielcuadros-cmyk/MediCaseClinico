package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.geo.distanceMeters
import com.example.medicaseclinico.domain.model.CaseLocation
import java.util.concurrent.TimeUnit

/**
 * Decide si una posición del estuche merece guardarse.
 *
 * El GPS envía una posición por segundo; guardarlas todas llenaría la base
 * con miles de filas casi idénticas de un estuche quieto. Se guarda:
 *  - la primera posición;
 *  - cuando el estuche se ha movido de verdad, como mucho una vez cada
 *    [minIntervalMs] para no escribir sin parar durante un trayecto;
 *  - y, aunque no se mueva, una vez cada [maxIntervalMs], para que la hora
 *    de la "última ubicación conocida" no se quede vieja.
 *
 * @param minDistanceMeters movimiento mínimo. Debe superar el vaivén normal
 * de un GPS quieto (varios metros), o se guardaría ruido como movimiento.
 */
class ShouldSaveCaseLocationUseCase(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val maxIntervalMs: Long = DEFAULT_MAX_INTERVAL_MS,
    private val minDistanceMeters: Double = DEFAULT_MIN_DISTANCE_METERS
) {
    /**
     * @param lastSaved última posición guardada, o null si aún no hay ninguna.
     * @param receivedAt momento de recepción de la posición nueva (epoch ms).
     */
    operator fun invoke(
        lastSaved: CaseLocation?,
        latitude: Double,
        longitude: Double,
        receivedAt: Long
    ): Boolean {
        if (lastSaved == null) return true

        val elapsedMs = receivedAt - lastSaved.timestamp
        // Reloj del teléfono atrasado: no se puede medir el tiempo, se guarda.
        if (elapsedMs < 0 || elapsedMs >= maxIntervalMs) return true
        if (elapsedMs < minIntervalMs) return false

        val movedMeters = distanceMeters(
            fromLatitude = lastSaved.latitude,
            fromLongitude = lastSaved.longitude,
            toLatitude = latitude,
            toLongitude = longitude
        )
        return movedMeters >= minDistanceMeters
    }

    companion object {
        val DEFAULT_MIN_INTERVAL_MS: Long = TimeUnit.SECONDS.toMillis(5)
        val DEFAULT_MAX_INTERVAL_MS: Long = TimeUnit.MINUTES.toMillis(1)
        const val DEFAULT_MIN_DISTANCE_METERS = 25.0
    }
}
