package com.example.medicaseclinico.ui.screens.lossmode

import androidx.compose.runtime.Immutable
import com.example.medicaseclinico.domain.model.RelativeTime

/**
 * Estado único e inmutable de la pantalla "Modo Pérdida".
 */
@Immutable
sealed interface LossModeUiState {

    /** Aún no se ha leído el estado guardado. */
    data object Loading : LossModeUiState

    /**
     * El modo está desactivado: la pantalla explica para qué sirve y ofrece
     * activarlo. Es el estado "vacío" de esta pantalla.
     */
    data class Inactive(
        val isBusy: Boolean,
        val message: LossModeMessage?
    ) : LossModeUiState

    /**
     * El modo está activo.
     *
     * @param lock lo que el estuche dice de su seguro ahora mismo.
     * @param position posición a mostrar, o null si no hay ninguna.
     * @param controls si el estuche acepta órdenes (alarma, GPS, seguro).
     * @param alarmOn la alarma quedó encendida en el estuche.
     * @param highFrequencyGps el GPS rápido quedó activado en el estuche.
     * @param gpsIntervalSeconds cada cuánto envía su posición el estuche con
     * los ajustes actuales.
     * @param offerLock hay que ofrecer activar el seguro (se acaba de activar
     * el modo con el seguro apagado).
     */
    data class Success(
        val lock: LossLockStatus,
        val position: LossPosition?,
        val controls: CaseControlsAvailability,
        val alarmOn: Boolean,
        val highFrequencyGps: Boolean,
        val gpsIntervalSeconds: Int,
        val offerLock: Boolean,
        val isBusy: Boolean,
        val message: LossModeMessage?
    ) : LossModeUiState

    /** No se pudo leer el estado guardado. Se ofrece reintentar. */
    data object Error : LossModeUiState
}

/** Lo que se sabe del seguro del estuche. Nunca se da por bloqueado sin que el estuche lo diga. */
enum class LossLockStatus {
    /** El estuche pide contraseña para abrirse. */
    LOCKED,

    /** El estuche se abre sin contraseña: conviene activar el seguro. */
    UNLOCKED,

    /** No hay enlace, o el estuche aún no lo ha informado. */
    UNKNOWN,

    /** El estuche conectado no tiene seguro. */
    NOT_SUPPORTED
}

/** Si el estuche puede recibir órdenes ahora. */
enum class CaseControlsAvailability { READY, DISCONNECTED, NOT_SUPPORTED }

/**
 * Posición del estuche a mostrar.
 *
 * @param isLive true si viene del GPS del estuche ahora; false si es la
 * última guardada.
 * @param elapsed cuánto hace que se recibió.
 */
@Immutable
data class LossPosition(
    val latitude: Double,
    val longitude: Double,
    val isLive: Boolean,
    val elapsed: RelativeTime
)

/** Resultado de una acción, para avisar al usuario una sola vez. */
enum class LossModeMessage {
    LOCK_ENABLED,
    NOT_SAVED,
    CASE_NOT_CONNECTED,
    CASE_NOT_SUPPORTED,
    PAIRING_REQUIRED,
    WRONG_PIN,
    CASE_LOCKED_OUT,
    INVALID_PIN,
    CASE_FAILED,

    /** El modo se desactivó, pero el estuche no recibió la orden de apagar la alarma o el GPS rápido. */
    DEACTIVATED_CASE_NOT_UPDATED
}
