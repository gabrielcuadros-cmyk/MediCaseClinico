package com.example.medicaseclinico.ui.screens.security

import androidx.compose.runtime.Immutable
import com.example.medicaseclinico.domain.model.AppearanceMode
import com.example.medicaseclinico.domain.model.UserRole

/**
 * Estado único e inmutable de la pantalla "Seguridad del Estuche".
 */
@Immutable
sealed interface SecurityUiState {

    /** Aún no hay respuesta del estuche ni de la nube. */
    data object Loading : SecurityUiState

    /**
     * @param pin lo que se sabe del seguro del estuche y si se puede cambiar.
     * @param users personas con acceso, por orden alfabético.
     * @param usersFailed la lista no se pudo leer. Va aparte y no como error
     * de toda la pantalla: la contraseña del estuche se gestiona por
     * Bluetooth y debe seguir disponible aunque falle la nube.
     * @param appearance apariencia elegida para la app (Sistema / Claro / Oscuro).
     * @param isBusy hay una orden al estuche o un borrado en curso.
     * @param message aviso pendiente de mostrar tras una acción.
     */
    data class Success(
        val pin: CasePinState,
        val users: List<AccessUser>,
        val usersFailed: Boolean,
        val appearance: AppearanceMode,
        val isBusy: Boolean,
        val message: SecurityMessage?
    ) : SecurityUiState

    /** Fallo inesperado al preparar la pantalla. Se ofrece reintentar. */
    data object Error : SecurityUiState
}

/** Por qué se puede, o no, gestionar la contraseña del estuche. */
enum class CasePinAvailability {
    /** El estuche está conectado y acepta órdenes. */
    READY,

    /** No hay enlace Bluetooth con el estuche. */
    DISCONNECTED,

    /** El estuche conectado no tiene estas funciones. */
    NOT_SUPPORTED
}

/**
 * @param pinRequired true si el estuche pide contraseña para abrir, o null si
 * no se sabe (sin enlace, o el estuche aún no lo ha dicho): el interruptor se
 * muestra entonces desactivado, no en una posición inventada.
 */
@Immutable
data class CasePinState(
    val availability: CasePinAvailability,
    val pinRequired: Boolean?
) {
    val canChange: Boolean get() = availability == CasePinAvailability.READY
}

/** Persona con acceso, lista para dibujar. */
@Immutable
data class AccessUser(
    val id: String,
    val name: String,
    val initials: String,
    val role: UserRole
)

/** Resultado de una acción, para avisar al usuario una sola vez. */
enum class SecurityMessage {
    PIN_CHANGED,
    LOCK_ENABLED,
    LOCK_DISABLED,
    USER_REMOVED,
    USER_REMOVE_FAILED,
    CASE_NOT_CONNECTED,
    CASE_NOT_SUPPORTED,
    PAIRING_REQUIRED,
    WRONG_PIN,
    CASE_LOCKED_OUT,
    INVALID_PIN,
    SECURE_CONNECTION_REQUIRED,
    CASE_FAILED,
    APPEARANCE_NOT_SAVED
}
