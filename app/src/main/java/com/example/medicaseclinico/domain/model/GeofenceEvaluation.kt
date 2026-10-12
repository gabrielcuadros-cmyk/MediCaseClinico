package com.example.medicaseclinico.domain.model

/** Cruce del borde de una zona de seguridad. */
enum class GeofenceTransitionType {
    /** El estuche estaba dentro y salió: es lo que se alerta. */
    EXITED,

    /** El estuche estaba fuera y volvió a entrar. */
    ENTERED
}

/**
 * El estuche cruzó el borde de [geofence].
 *
 * Lleva la zona entera y no solo su id porque la alerta necesita el nombre.
 */
data class GeofenceTransition(
    val geofence: Geofence,
    val type: GeofenceTransitionType
)

/**
 * Resultado de evaluar las zonas con una posición del estuche.
 *
 * @param insideById id de cada zona activa → true si el estuche está dentro.
 * Es el estado que hay que devolver en la siguiente evaluación, y el que
 * alimenta los chips "Dentro" / "Fuera".
 * @param transitions zonas cuyo borde se cruzó con esta posición.
 */
data class GeofenceEvaluation(
    val insideById: Map<Long, Boolean>,
    val transitions: List<GeofenceTransition>
)
