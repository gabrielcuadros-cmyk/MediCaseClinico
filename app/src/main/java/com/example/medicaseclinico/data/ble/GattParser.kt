package com.example.medicaseclinico.data.ble

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