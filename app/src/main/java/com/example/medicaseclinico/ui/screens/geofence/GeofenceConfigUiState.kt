package com.example.medicaseclinico.ui.screens.geofence

import androidx.compose.runtime.Immutable
import com.example.medicaseclinico.domain.geo.GeoPoint

/**
 * Estado único e inmutable de la pantalla "Configurar Geocerca".
 *
 * No tiene un estado vacío de pantalla completa: sin zonas guardadas el
 * formulario sigue siendo útil, y la lista vacía se explica dentro de [Success].
 * El texto del nombre no va aquí sino en el ViewModel como estado de Compose,
 * para que el campo no pierda letras al escribir rápido.
 */
@Immutable
sealed interface GeofenceConfigUiState {

    /** Aún no hay respuesta de Room. */
    data object Loading : GeofenceConfigUiState

    /**
     * @param radiusMeters radio elegido para la zona nueva.
     * @param hasCenter false mientras el usuario no haya situado el mapa: la
     * vista inicial del mundo no es un lugar elegido.
     * @param canSave hay nombre y centro, y no hay un guardado en curso.
     * @param mapStart dónde abrir el mapa: el estuche, o si no se sabe dónde
     * está, la primera zona guardada. null si no hay ningún lugar conocido.
     * @param casePosition posición del estuche (en vivo o última conocida)
     * para dibujar su marcador, o null si no hay ninguna.
     * @param savedZones zonas guardadas, en el orden en que se crearon.
     * @param message aviso pendiente de mostrar tras intentar guardar.
     */
    data class Success(
        val radiusMeters: Int,
        val hasCenter: Boolean,
        val canSave: Boolean,
        val isSaving: Boolean,
        val mapStart: GeoPoint?,
        val casePosition: GeoPoint?,
        val savedZones: List<SavedZone>,
        val message: GeofenceConfigMessage?
    ) : GeofenceConfigUiState

    /** Fallo controlado al leer las zonas o las posiciones. La pantalla ofrece reintentar. */
    data object Error : GeofenceConfigUiState
}

/**
 * Zona guardada lista para la lista y para el mapa.
 *
 * @param isInside true/false si se sabe dónde está el estuche; null si no se
 * sabe (o la zona está inactiva), y entonces no se muestra ni "Dentro" ni "Fuera".
 */
@Immutable
data class SavedZone(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val isInside: Boolean?
)

/** Resultado de guardar, para avisar al usuario una sola vez. */
enum class GeofenceConfigMessage { SAVED, SAVE_FAILED }
