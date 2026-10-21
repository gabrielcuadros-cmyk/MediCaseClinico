package com.example.medicaseclinico.domain.model

/** Posición del cierre electrónico (el servomotor) del estuche. */
enum class LockState { LOCKED, OPEN }

/**
 * Lo que la app sabe **en vivo** del cierre y del PIN del estuche.
 *
 * Como con la ubicación, se distingue por qué no hay dato: sin enlace se pide
 * conectar el estuche, y un estuche sin estas funciones no debe ofrecerlas.
 */
sealed interface CaseControlState {

    /** No hay enlace con el estuche. */
    data object Unavailable : CaseControlState

    /** El estuche conectado no tiene el servicio de control (firmware anterior). */
    data object NotSupported : CaseControlState

    /**
     * El estuche acepta órdenes.
     *
     * @param lockState posición del cierre, o null si aún no la ha informado.
     * @param pinRequired true si hay que teclear el PIN para abrir (seguro
     * activado), o null si aún no lo ha informado.
     */
    data class Ready(
        val lockState: LockState? = null,
        val pinRequired: Boolean? = null
    ) : CaseControlState
}

/** Qué pasó en el teclado del estuche. */
enum class AccessEventType {
    /** Se abrió con el PIN correcto. */
    GRANTED,

    /** Se tecleó un PIN incorrecto. */
    DENIED,

    /** El estuche bloqueó el teclado por demasiados intentos fallidos. */
    LOCKED_OUT
}

/**
 * Intento de abrir el estuche desde su teclado.
 *
 * @param failedAttempts intentos fallidos seguidos que lleva el estuche.
 * @param receivedAt momento en que el teléfono recibió el aviso (epoch ms).
 * El estuche no tiene reloj: esta es la única hora que existe del evento.
 */
data class AccessEvent(
    val type: AccessEventType,
    val failedAttempts: Int,
    val receivedAt: Long
)

/** Motivos por los que el estuche no ejecuta una orden. */
enum class CaseCommandError {
    /** No hay enlace con el estuche. */
    NOT_CONNECTED,

    /** El estuche conectado no tiene esa función. */
    NOT_SUPPORTED,

    /** El teléfono no está emparejado con el estuche; Android pide emparejarlo. */
    PAIRING_REQUIRED,

    /** El PIN actual enviado no es el del estuche. */
    WRONG_PIN,

    /** El estuche está bloqueado por intentos fallidos y no acepta PIN por ahora. */
    LOCKED_OUT,

    /** El valor no es válido (lo detectó la app o lo rechazó el estuche). */
    INVALID_VALUE,

    /**
     * La orden lleva el PIN y la conexión no está cifrada (MQTT sin TLS): no se envía,
     * para que el PIN nunca viaje en claro por el broker.
     */
    SECURE_CONNECTION_REQUIRED,

    /** El estuche no respondió o respondió con un error desconocido. */
    FAILED
}

/** Fallo de una orden al estuche, ya clasificado; viaja dentro de `Result.failure`. */
class CaseCommandException(val error: CaseCommandError) : Exception(error.name)

/**
 * Reglas del PIN del estuche: cuatro dígitos, los que tiene su teclado.
 *
 * La app nunca guarda un PIN: lo valida, lo envía y lo olvida.
 */
object CasePin {
    const val LENGTH = 4

    fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }
}

/** Segundos entre dos posiciones del GPS del estuche (valores de fábrica del protocolo). */
object GpsInterval {
    const val MIN_SECONDS = 1
    const val MAX_SECONDS = 60
    const val DEFAULT_SECONDS = 5

    /** Máxima frecuencia, para seguir un estuche perdido. */
    const val LOSS_MODE_SECONDS = 1

    fun isValid(seconds: Int): Boolean = seconds in MIN_SECONDS..MAX_SECONDS
}
