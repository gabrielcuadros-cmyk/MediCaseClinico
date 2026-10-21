package com.example.medicaseclinico.data.mqtt

import com.example.medicaseclinico.domain.model.AccessEvent
import com.example.medicaseclinico.domain.model.AccessEventType
import com.example.medicaseclinico.domain.model.CaseCommandError
import com.example.medicaseclinico.domain.model.CaseGpsReading
import com.example.medicaseclinico.domain.model.CasePin
import com.example.medicaseclinico.domain.model.GpsInterval
import com.example.medicaseclinico.domain.model.LockState
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.floor

/** Nombres de los topics, relativos a la raíz de [MqttConfig.topicBase]. */
object MqttTopic {
    const val STATE = "estado"
    const val TEMPERATURE = "temperatura"
    const val BATTERY = "bateria"
    const val LOCATION = "ubicacion"
    const val LOCK = "cierre"
    const val ACCESS = "acceso"
    const val ORDER_PIN = "orden/pin"
    const val ORDER_PIN_REQUIRED = "orden/seguro"
    const val ORDER_ALARM = "orden/alarma"
    const val ORDER_GPS = "orden/gps"
    const val ORDER_REPLY = "orden/respuesta"
}

/**
 * Mensajes JSON del estuche por MQTT. El formato de cada uno está en
 * `hardware/mqtt-protocol.md`, que es el contrato con el firmware: si cambia algo
 * aquí, cambia allí.
 *
 * Mismas reglas que [com.example.medicaseclinico.data.ble.ControlCodec]: funciones
 * puras y sin excepciones. Lo que no se entiende se descarta (null) en lugar de
 * interpretarse a medias: un campo con un tipo inesperado invalida el mensaje entero.
 */
object MqttCodec {

    // Rango físico del DS18B20, el mismo que acepta la app por Bluetooth.
    private const val SENSOR_MIN_CELSIUS = -40.0
    private const val SENSOR_MAX_CELSIUS = 125.0

    private const val BATTERY_MAX_PERCENT = 100
    private const val MAX_FAILED_ATTEMPTS = 255
    private const val MAX_LATITUDE = 90.0
    private const val MAX_LONGITUDE = 180.0

    private const val CLOSED = "cerrado"
    private const val OPEN = "abierto"

    private const val ACCESS_GRANTED = "concedido"
    private const val ACCESS_DENIED = "denegado"
    private const val ACCESS_LOCKED_OUT = "bloqueo"

    private const val ERROR_WRONG_PIN = "pin_incorrecto"
    private const val ERROR_LOCKED_OUT = "bloqueado"
    private const val ERROR_INVALID_VALUE = "valor_invalido"

    /**
     * Lo que el estuche publica en `cierre`. Cada campo es null si el mensaje no lo
     * trae; el estuche puede informar solo lo que cambió.
     */
    data class LockReport(val lockState: LockState?, val pinRequired: Boolean?)

    /**
     * Respuesta del estuche a una orden.
     *
     * @param error null si la ejecutó; si no, el motivo ya clasificado.
     */
    data class CommandReply(val commandId: String, val error: CaseCommandError?)

    /** `estado`: true si el estuche está conectado al broker; null si no se entiende. */
    fun parseOnline(payload: String): Boolean? =
        parseObject(payload)?.booleanOrNull("online")

    /**
     * `temperatura`: grados Celsius, o null fuera del rango físico del sensor
     * (−127 °C y 85 °C del DS18B20 no son temperaturas, ya los descarta el firmware).
     */
    fun parseTemperatureCelsius(payload: String): Float? {
        val celsius = parseObject(payload)?.numberOrNull("celsius") ?: return null
        if (celsius !in SENSOR_MIN_CELSIUS..SENSOR_MAX_CELSIUS) return null
        return celsius.toFloat()
    }

    /** `bateria`: porcentaje entero de 0 a 100. */
    fun parseBatteryPercent(payload: String): Int? =
        parseObject(payload)?.intOrNull("porcentaje")?.takeIf { it in 0..BATTERY_MAX_PERCENT }

    /**
     * `ubicacion`: posición con fix, [CaseGpsReading.NoSignal] sin fix, o null si el
     * mensaje no es válido. Unas coordenadas fuera de rango no se "corrigen": una
     * posición inventada dispararía geocercas.
     */
    fun parseLocation(payload: String): CaseGpsReading? {
        val json = parseObject(payload) ?: return null
        return when (json.booleanOrNull("fix")) {
            false -> CaseGpsReading.NoSignal
            true -> {
                val latitude = json.numberOrNull("lat") ?: return null
                val longitude = json.numberOrNull("lng") ?: return null
                if (abs(latitude) > MAX_LATITUDE || abs(longitude) > MAX_LONGITUDE) return null
                CaseGpsReading.Fix(latitude = latitude, longitude = longitude)
            }
            null -> null
        }
    }

    /** `cierre`: posición del cierre y estado del seguro. */
    fun parseLock(payload: String): LockReport? {
        val json = parseObject(payload) ?: return null
        val lockState = if (json.has("cierre")) {
            when (json.stringOrNull("cierre")) {
                CLOSED -> LockState.LOCKED
                OPEN -> LockState.OPEN
                else -> return null
            }
        } else {
            null
        }
        val pinRequired = if (json.has("seguro")) json.booleanOrNull("seguro") ?: return null else null
        if (lockState == null && pinRequired == null) return null
        return LockReport(lockState = lockState, pinRequired = pinRequired)
    }

    /**
     * `acceso`: intento de abrir desde el teclado.
     *
     * @param receivedAt hora de recepción (epoch ms): el estuche no tiene reloj.
     */
    fun parseAccessEvent(payload: String, receivedAt: Long): AccessEvent? {
        val json = parseObject(payload) ?: return null
        val type = when (json.stringOrNull("tipo")) {
            ACCESS_GRANTED -> AccessEventType.GRANTED
            ACCESS_DENIED -> AccessEventType.DENIED
            ACCESS_LOCKED_OUT -> AccessEventType.LOCKED_OUT
            else -> return null
        }
        val failedAttempts = json.intOrNull("intentosFallidos")
            ?.takeIf { it in 0..MAX_FAILED_ATTEMPTS }
            ?: return null
        return AccessEvent(type = type, failedAttempts = failedAttempts, receivedAt = receivedAt)
    }

    /** `orden/respuesta`: resultado de una orden. Un error desconocido cuenta como fallo. */
    fun parseCommandReply(payload: String): CommandReply? {
        val json = parseObject(payload) ?: return null
        val commandId = json.stringOrNull("id")?.takeIf { it.isNotBlank() } ?: return null
        val ok = json.booleanOrNull("ok") ?: return null
        if (ok) return CommandReply(commandId = commandId, error = null)
        val error = when (json.stringOrNull("error")) {
            ERROR_WRONG_PIN -> CaseCommandError.WRONG_PIN
            ERROR_LOCKED_OUT -> CaseCommandError.LOCKED_OUT
            ERROR_INVALID_VALUE -> CaseCommandError.INVALID_VALUE
            else -> CaseCommandError.FAILED
        }
        return CommandReply(commandId = commandId, error = error)
    }

    /** `orden/pin`. null si alguno de los PIN no son 4 dígitos. */
    fun encodePinChange(commandId: String, currentPin: String, newPin: String): String? {
        if (!CasePin.isValid(currentPin) || !CasePin.isValid(newPin)) return null
        return JSONObject()
            .put("id", commandId)
            .put("pinActual", currentPin)
            .put("pinNuevo", newPin)
            .toString()
    }

    /** `orden/seguro`. null si el PIN actual no son 4 dígitos. */
    fun encodePinRequired(commandId: String, required: Boolean, currentPin: String): String? {
        if (!CasePin.isValid(currentPin)) return null
        return JSONObject()
            .put("id", commandId)
            .put("activar", required)
            .put("pinActual", currentPin)
            .toString()
    }

    /** `orden/alarma`. */
    fun encodeAlarm(commandId: String, on: Boolean): String =
        JSONObject()
            .put("id", commandId)
            .put("encender", on)
            .toString()

    /** `orden/gps`. null si el intervalo está fuera de lo que admite el protocolo. */
    fun encodeGpsInterval(commandId: String, seconds: Int): String? {
        if (!GpsInterval.isValid(seconds)) return null
        return JSONObject()
            .put("id", commandId)
            .put("segundos", seconds)
            .toString()
    }

    private fun parseObject(payload: String): JSONObject? = try {
        JSONObject(payload)
    } catch (e: JSONException) {
        null
    }

    // Lectura estricta: "true" entre comillas no es un booleano ni "4.2" un número.
    private fun JSONObject.booleanOrNull(key: String): Boolean? = opt(key) as? Boolean

    private fun JSONObject.stringOrNull(key: String): String? = opt(key) as? String

    private fun JSONObject.numberOrNull(key: String): Double? =
        (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }

    private fun JSONObject.intOrNull(key: String): Int? =
        numberOrNull(key)?.takeIf { it == floor(it) && abs(it) <= Int.MAX_VALUE }?.toInt()
}
