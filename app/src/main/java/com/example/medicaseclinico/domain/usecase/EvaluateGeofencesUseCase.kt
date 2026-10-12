package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.geo.distanceMeters
import com.example.medicaseclinico.domain.model.Geofence
import com.example.medicaseclinico.domain.model.GeofenceEvaluation
import com.example.medicaseclinico.domain.model.GeofenceTransition
import com.example.medicaseclinico.domain.model.GeofenceTransitionType

/**
 * Decide, con una posición del GPS del estuche, en qué zonas de seguridad
 * está y cuáles acaba de cruzar.
 *
 * Usa histéresis: un GPS quieto baila varios metros, y sin margen un estuche
 * parado justo en el borde alternaría dentro/fuera y alertaría sin parar.
 * Por eso para salir hay que alejarse [hysteresisMeters] más allá del radio,
 * y para volver a entrar hay que meterse ese mismo margen hacia dentro.
 *
 * No guarda estado: quien lo llama conserva el `insideById` del resultado y
 * lo devuelve en la siguiente evaluación. Así se prueba con tests JVM.
 *
 * @param hysteresisMeters margen a cada lado del borde. Debe superar el
 * vaivén normal de un GPS quieto.
 */
class EvaluateGeofencesUseCase(
    private val hysteresisMeters: Double = DEFAULT_HYSTERESIS_METERS
) {
    /**
     * @param geofences zonas guardadas; las inactivas se ignoran.
     * @param previousInsideById resultado de la evaluación anterior, o vacío
     * si es la primera.
     */
    operator fun invoke(
        geofences: List<Geofence>,
        latitude: Double,
        longitude: Double,
        previousInsideById: Map<Long, Boolean> = emptyMap()
    ): GeofenceEvaluation {
        val insideById = mutableMapOf<Long, Boolean>()
        val transitions = mutableListOf<GeofenceTransition>()

        // Las zonas inactivas o borradas no pasan al resultado: si luego se
        // reactivan, empiezan de cero en vez de comparar con un estado viejo.
        geofences.filter { it.isActive }.forEach { geofence ->
            val distance = distanceMeters(
                fromLatitude = geofence.latitude,
                fromLongitude = geofence.longitude,
                toLatitude = latitude,
                toLongitude = longitude
            )
            val wasInside = previousInsideById[geofence.id]
            val isInside = when (wasInside) {
                // Primera vez que se evalúa la zona: no hay de dónde venir, así
                // que se clasifica con el radio tal cual y no cuenta como cruce.
                null -> distance <= geofence.radiusMeters
                true -> distance <= geofence.radiusMeters + hysteresisMeters
                false -> distance < entryRadiusMeters(geofence.radiusMeters)
            }

            insideById[geofence.id] = isInside
            if (wasInside != null && wasInside != isInside) {
                transitions += GeofenceTransition(
                    geofence = geofence,
                    type = if (isInside) GeofenceTransitionType.ENTERED else GeofenceTransitionType.EXITED
                )
            }
        }
        return GeofenceEvaluation(insideById = insideById, transitions = transitions)
    }

    /**
     * Distancia al centro por debajo de la cual se vuelve a estar dentro.
     *
     * En una zona pequeña el margen completo dejaría un círculo de entrada
     * diminuto o inexistente, y no se podría volver a entrar nunca; ahí el
     * margen se limita a la mitad del radio.
     */
    private fun entryRadiusMeters(radiusMeters: Double): Double =
        radiusMeters - minOf(hysteresisMeters, radiusMeters / 2)

    companion object {
        const val DEFAULT_HYSTERESIS_METERS = 20.0
    }
}
