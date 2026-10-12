package com.example.medicaseclinico.ui.screens.map

import androidx.compose.runtime.Immutable
import com.example.medicaseclinico.domain.model.RelativeTime

/**
 * Estado único e inmutable de la pantalla "Geolocalización".
 *
 * La posición siempre es del GPS del estuche: en vivo, o la última que quedó
 * guardada. Nunca la del teléfono ni una inventada.
 */
@Immutable
sealed interface MapUiState {

    /** Aún no hay respuesta de Room. */
    data object Loading : MapUiState

    /**
     * El estuche no informa posición ahora y nunca se guardó ninguna: no hay
     * nada que poner en el mapa. [liveStatus] explica por qué.
     */
    data class Empty(val liveStatus: CaseLiveStatus) : MapUiState

    /**
     * Hay una posición que mostrar.
     *
     * @param updatedAt momento en que el teléfono recibió esa posición (epoch ms).
     * @param elapsed tiempo desde [updatedAt], ya calculado para no hacerlo al dibujar.
     * @param liveStatus [CaseLiveStatus.LIVE] si la posición es la actual; si
     * no, es la última conocida y este valor explica por qué no hay una nueva.
     * @param geofences zonas activas, de la más cercana al estuche a la más lejana.
     */
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val updatedAt: Long,
        val elapsed: RelativeTime,
        val liveStatus: CaseLiveStatus,
        val geofences: List<MapGeofence>
    ) : MapUiState {
        /** La zona que resume la tarjeta "Geocerca de Seguridad", o null si no hay ninguna activa. */
        val nearestGeofence: MapGeofence? get() = geofences.firstOrNull()
    }

    /** Fallo controlado al leer las posiciones o las zonas. La pantalla ofrece reintentar. */
    data object Error : MapUiState
}

/** Qué dice ahora el estuche sobre su ubicación. */
enum class CaseLiveStatus {
    /** El GPS del estuche está enviando su posición. */
    LIVE,

    /** Hay enlace, pero el GPS no tiene posición (interiores, arranque en frío). */
    NO_GPS_SIGNAL,

    /** No hay enlace Bluetooth con el estuche, o aún no ha enviado su ubicación. */
    NO_LINK,

    /** El estuche conectado no tiene GPS. */
    GPS_NOT_SUPPORTED
}

/**
 * Zona de seguridad lista para dibujar.
 *
 * @param isInside true si la posición mostrada está dentro de la zona.
 */
@Immutable
data class MapGeofence(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val isInside: Boolean
)
