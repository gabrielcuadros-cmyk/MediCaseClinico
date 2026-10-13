package com.example.medicaseclinico.data.ble

import com.example.medicaseclinico.domain.model.AccessEventType
import com.example.medicaseclinico.domain.model.CaseCommandError
import com.example.medicaseclinico.domain.model.CasePin
import com.example.medicaseclinico.domain.model.GpsInterval
import com.example.medicaseclinico.domain.model.LockState

/**
 * Tramas del servicio propio "MediCase Control" (cierre, PIN, accesos, alarma
 * y frecuencia del GPS). El formato de cada una está en
 * `hardware/ble-protocol.md`, que es el contrato con el firmware: si cambia
 * algo aquí, cambia allí.
 *
 * Funciones puras, como [GattParser]: sin Android y sin excepciones. Lo que
 * no se entiende se descarta (null) en vez de interpretarse a medias; con un
 * cierre de por medio, es más seguro no saber que suponer.
 */
object ControlCodec {

    private const val LOCKED: Byte = 0x00
    private const val OPEN: Byte = 0x01

    private const val DISABLED: Byte = 0x00
    private const val ENABLED: Byte = 0x01

    private const val ACCESS_EVENT_SIZE = 2
    private const val ACCESS_GRANTED = 0x01
    private const val ACCESS_DENIED = 0x02
    private const val ACCESS_LOCKED_OUT = 0x03

    // Respuestas de error ATT a una escritura.
    private const val STATUS_SUCCESS = 0x00
    private const val STATUS_INSUFFICIENT_AUTHENTICATION = 0x05
    private const val STATUS_INSUFFICIENT_AUTHORIZATION = 0x08
    private const val STATUS_INSUFFICIENT_ENCRYPTION = 0x0F
    private const val STATUS_ANDROID_AUTH_FAIL = 0x89
    private const val STATUS_WRONG_PIN = 0x80
    private const val STATUS_INVALID_VALUE = 0x81
    private const val STATUS_LOCKED_OUT = 0x82

    /** Evento del teclado tal como llega: sin hora, que la pone quien lo recibe. */
    data class AccessEventFrame(val type: AccessEventType, val failedAttempts: Int)

    fun parseLockState(payload: ByteArray): LockState? = when (payload.firstOrNull()) {
        LOCKED -> LockState.LOCKED
        OPEN -> LockState.OPEN
        else -> null
    }

    /** @return true si el seguro está activado (se pide PIN), o null si la trama no es válida. */
    fun parsePinRequired(payload: ByteArray): Boolean? = when (payload.firstOrNull()) {
        DISABLED -> false
        ENABLED -> true
        else -> null
    }

    fun parseAccessEvent(payload: ByteArray): AccessEventFrame? {
        if (payload.size < ACCESS_EVENT_SIZE) return null
        val type = when (payload[0].toInt() and 0xFF) {
            ACCESS_GRANTED -> AccessEventType.GRANTED
            ACCESS_DENIED -> AccessEventType.DENIED
            ACCESS_LOCKED_OUT -> AccessEventType.LOCKED_OUT
            else -> return null
        }
        return AccessEventFrame(type = type, failedAttempts = payload[1].toInt() and 0xFF)
    }

    /** Activar o desactivar el seguro: estado + PIN actual. null si el PIN no son 4 dígitos. */
    fun encodePinRequired(required: Boolean, currentPin: String): ByteArray? {
        val pin = encodePin(currentPin) ?: return null
        return byteArrayOf(if (required) ENABLED else DISABLED) + pin
    }

    /** Cambio de PIN: PIN actual + PIN nuevo. null si alguno no son 4 dígitos. */
    fun encodePinChange(currentPin: String, newPin: String): ByteArray? {
        val current = encodePin(currentPin) ?: return null
        val new = encodePin(newPin) ?: return null
        return current + new
    }

    fun encodeAlarm(on: Boolean): ByteArray = byteArrayOf(if (on) ENABLED else DISABLED)

    /** null si el intervalo está fuera de lo que admite el protocolo. */
    fun encodeGpsInterval(seconds: Int): ByteArray? =
        if (GpsInterval.isValid(seconds)) byteArrayOf(seconds.toByte()) else null

    /**
     * Motivo del fallo de una escritura a partir del código que devuelve el
     * estuche, o null si la aceptó.
     */
    fun errorFromWriteStatus(status: Int): CaseCommandError? = when (status) {
        STATUS_SUCCESS -> null
        STATUS_INSUFFICIENT_AUTHENTICATION,
        STATUS_INSUFFICIENT_AUTHORIZATION,
        STATUS_INSUFFICIENT_ENCRYPTION,
        STATUS_ANDROID_AUTH_FAIL -> CaseCommandError.PAIRING_REQUIRED
        STATUS_WRONG_PIN -> CaseCommandError.WRONG_PIN
        STATUS_INVALID_VALUE -> CaseCommandError.INVALID_VALUE
        STATUS_LOCKED_OUT -> CaseCommandError.LOCKED_OUT
        else -> CaseCommandError.FAILED
    }

    /** Dígitos ASCII, como los teclea el usuario en el estuche. */
    private fun encodePin(pin: String): ByteArray? =
        if (CasePin.isValid(pin)) pin.toByteArray(Charsets.US_ASCII) else null
}
