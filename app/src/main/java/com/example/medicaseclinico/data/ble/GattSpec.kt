package com.example.medicaseclinico.data.ble

import com.example.medicaseclinico.BuildConfig
import java.util.UUID

/**
 * UUIDs GATT del estuche MediCase.
 *
 * Por defecto: perfiles estándar del Bluetooth SIG (públicos, no secretos).
 * Si en local.properties se definen BLE_*_UUID (firmware propio), se usan
 * esos; un valor vacío o mal formado cae al estándar sin romper la app.
 */
object GattSpec {

    /** Base UUID del Bluetooth SIG: 0000xxxx-0000-1000-8000-00805f9b34fb */
    private fun sigUuid(shortUuid: String): UUID =
        UUID.fromString("0000$shortUuid-0000-1000-8000-00805f9b34fb")

    /** Health Thermometer Service (0x1809): filtro de escaneo del estuche. */
    val THERMOMETER_SERVICE: UUID =
        resolve(BuildConfig.BLE_SERVICE_UUID, sigUuid("1809"))

    /** Temperature Measurement (0x2A1C): IEEE-11073 FLOAT, vía Indicate. */
    val TEMPERATURE_MEASUREMENT: UUID =
        resolve(BuildConfig.BLE_TEMPERATURE_CHAR_UUID, sigUuid("2a1c"))

    /** Battery Level (0x2A19): uint8 0–100, lectura y Notify opcional. */
    val BATTERY_LEVEL: UUID =
        resolve(BuildConfig.BLE_BATTERY_CHAR_UUID, sigUuid("2a19"))

    /** Client Characteristic Configuration Descriptor: activa Notify/Indicate. */
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = sigUuid("2902")

    private fun resolve(override: String, default: UUID): UUID =
        override.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: default
}