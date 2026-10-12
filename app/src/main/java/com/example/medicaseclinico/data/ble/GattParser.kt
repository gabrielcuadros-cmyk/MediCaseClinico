package com.example.medicaseclinico.data.ble

import com.example.medicaseclinico.domain.model.CaseGpsReading
import kotlin.math.pow

/**
 * Decodificador de las características estándar del Bluetooth SIG que
 * expone el estuche MediCase.
 *
 * Funciones puras: sin Android, sin estado, sin excepciones hacia afuera.
 * Ante una trama corrupta, incompleta o fuera de rango devuelven null.
 */
object GattParser {

    // --- Temperature Measurement (0x2A1C) ---
    private const val FLAG_UNIT_FAHRENHEIT = 0x01
    private const val TEMPERATURE_VALUE_OFFSET = 1
    private const val MIN_TEMPERATURE_PAYLOAD_SIZE = 5 // 1 byte flags + 4 bytes FLOAT

    // Valores especiales de IEEE-11073 FLOAT (mantisa con exponente 0).
    private const val MANTISSA_NAN = 0x007FFFFF
    private const val MANTISSA_NRES = 0x00800000
    private const val MANTISSA_POSITIVE_INFINITY = 0x007FFFFE
    private const val MANTISSA_NEGATIVE_INFINITY = 0x00800002
    private const val MANTISSA_RESERVED = 0x00800001

    /**
     * Rango físicamente plausible de un sensor de temperatura ambiente.
     */
    private const val SENSOR_MIN_CELSIUS = -40f
    private const val SENSOR_MAX_CELSIUS = 125f

    // --- Battery Level (0x2A19) ---
    private const val BATTERY_MAX_PERCENT = 100

    // --- Location and Speed (0x2A67) ---
    private const val LOCATION_FLAGS_SIZE = 2 // uint16
    private const val FLAG_INSTANTANEOUS_SPEED_PRESENT = 0x0001
    private const val FLAG_TOTAL_DISTANCE_PRESENT = 0x0002
    private const val FLAG_LOCATION_PRESENT = 0x0004
    private const val INSTANTANEOUS_SPEED_SIZE = 2 // uint16
    private const val TOTAL_DISTANCE_SIZE = 3 // uint24
    private const val COORDINATE_SIZE = 4 // sint32
    private const val LOCATION_SIZE = 2 * COORDINATE_SIZE // latitud + longitud

    // Estado de la posición: bits 7 y 8 de las banderas.
    private const val POSITION_STATUS_SHIFT = 7
    private const val POSITION_STATUS_MASK = 0x03
    private const val POSITION_STATUS_OK = 1
    private const val POSITION_STATUS_ESTIMATED = 2

    private const val COORDINATE_RESOLUTION_DEGREES = 1e-7
    private const val MAX_LATITUDE = 90.0
    private const val MAX_LONGITUDE = 180.0

    /**
     * Decodifica una notificación de Temperature Measurement.
     *
     * @return temperatura en grados Celsius, o null si la trama es inválida.
     */
    fun parseTemperatureCelsius(payload: ByteArray): Float? {
        if (payload.size < MIN_TEMPERATURE_PAYLOAD_SIZE) return null
        val flags = payload[0].toInt() and 0xFF
        val rawValue = decodeIeee11073Float(payload, TEMPERATURE_VALUE_OFFSET) ?: return null
        val celsius = if (flags and FLAG_UNIT_FAHRENHEIT != 0) {
            (rawValue - 32.0) * 5.0 / 9.0
        } else {
            rawValue
        }.toFloat()
        return celsius.takeIf { it.isFinite() && it in SENSOR_MIN_CELSIUS..SENSOR_MAX_CELSIUS }
    }

    /**
     * Decodifica una lectura o notificación de Battery Level.
     *
     * @return porcentaje 0..100, o null si la trama está vacía o fuera de rango.
     */
    fun parseBatteryPercent(payload: ByteArray): Int? {
        if (payload.isEmpty()) return null
        val level = payload[0].toInt() and 0xFF
        return level.takeIf { it <= BATTERY_MAX_PERCENT }
    }

    /**
     * Decodifica una notificación de Location and Speed.
     *
     * Los campos son opcionales y van en orden fijo; las banderas dicen cuáles
     * vienen. Solo interesa la posición, pero hay que saltar velocidad y
     * distancia si están presentes porque van antes.
     *
     * Solo se acepta como posición la que el estuche marca como válida
     * ("Position Ok" o "Estimated Position"). "Last Known Position" es una
     * posición antigua que el GPS repite al perder la señal: se trata como
     * sin señal, porque la última ubicación conocida ya la guarda la app con
     * su hora real.
     *
     * @return posición o sin señal, o null si la trama está incompleta o trae
     * coordenadas imposibles.
     */
    fun parseLocationAndSpeed(payload: ByteArray): CaseGpsReading? {
        if (payload.size < LOCATION_FLAGS_SIZE) return null
        val flags = readUInt16(payload, 0)
        val positionStatus = (flags shr POSITION_STATUS_SHIFT) and POSITION_STATUS_MASK
        val hasLocation = flags and FLAG_LOCATION_PRESENT != 0

        var offset = LOCATION_FLAGS_SIZE
        if (flags and FLAG_INSTANTANEOUS_SPEED_PRESENT != 0) offset += INSTANTANEOUS_SPEED_SIZE
        if (flags and FLAG_TOTAL_DISTANCE_PRESENT != 0) offset += TOTAL_DISTANCE_SIZE
        val locationEnd = if (hasLocation) offset + LOCATION_SIZE else offset
        if (payload.size < locationEnd) return null

        val isValidPosition =
            positionStatus == POSITION_STATUS_OK || positionStatus == POSITION_STATUS_ESTIMATED
        if (!hasLocation || !isValidPosition) return CaseGpsReading.NoSignal

        val latitude = readSInt32(payload, offset) * COORDINATE_RESOLUTION_DEGREES
        val longitude = readSInt32(payload, offset + COORDINATE_SIZE) * COORDINATE_RESOLUTION_DEGREES
        if (latitude !in -MAX_LATITUDE..MAX_LATITUDE) return null
        if (longitude !in -MAX_LONGITUDE..MAX_LONGITUDE) return null
        return CaseGpsReading.Fix(latitude = latitude, longitude = longitude)
    }

    private fun readUInt16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readSInt32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            (bytes[offset + 3].toInt() shl 24)

    /**
     * IEEE-11073 32-bit FLOAT (little-endian):
     *   bytes [0..2] → mantisa con signo de 24 bits
     *   byte  [3]    → exponente con signo de 8 bits
     *   valor = mantisa × 10^exponente
     */
    internal fun decodeIeee11073Float(bytes: ByteArray, offset: Int): Double? {
        if (offset < 0 || bytes.size < offset + 4) return null
        val raw = (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                (bytes[offset + 3].toInt() shl 24)
        val unsignedMantissa = raw and 0x00FFFFFF
        if (unsignedMantissa in SPECIAL_MANTISSAS) return null

        val mantissa = if (unsignedMantissa >= 0x00800000) {
            unsignedMantissa - 0x01000000
        } else {
            unsignedMantissa
        }
        val exponent = raw shr 24
        return mantissa * 10.0.pow(exponent)
    }

    private val SPECIAL_MANTISSAS = setOf(
        MANTISSA_NAN,
        MANTISSA_NRES,
        MANTISSA_POSITIVE_INFINITY,
        MANTISSA_NEGATIVE_INFINITY,
        MANTISSA_RESERVED
    )
}