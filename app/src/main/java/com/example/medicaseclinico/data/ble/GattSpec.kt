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

    /**
     * Location and Navigation Service (0x1819): GPS del estuche. Opcional,
     * como la batería: un estuche sin este servicio sigue funcionando.
     */
    val LOCATION_AND_NAVIGATION_SERVICE: UUID = sigUuid("1819")

    /** Location and Speed (0x2A67): latitud y longitud en 1e-7 grados, vía Notify. */
    val LOCATION_AND_SPEED: UUID = sigUuid("2a67")

    // ------------------------------------------------------------------
    //  Servicio propio "MediCase Control" (ver hardware/ble-protocol.md)
    // ------------------------------------------------------------------
    /** Base propia: los bytes centrales son "MEDICASE" en ASCII. */
    private fun controlUuid(shortId: String): UUID =
        UUID.fromString("4d43$shortId-4d45-4449-4341-534500000000")

    /** Cierre, PIN, accesos, alarma y frecuencia del GPS. Opcional. */
    val CONTROL_SERVICE: UUID = controlUuid("0001")

    /** Estado del cierre: 0 cerrado, 1 abierto. Read y Notify. */
    val LOCK_STATE: UUID = controlUuid("0002")

    /** Seguro (PIN requerido): Read 1 byte; Write cifrada de estado + PIN actual. */
    val PIN_REQUIRED: UUID = controlUuid("0003")

    /** Cambio de PIN: Write cifrada de PIN actual + PIN nuevo. No se puede leer. */
    val PIN_CHANGE: UUID = controlUuid("0004")

    /** Evento de acceso desde el teclado: tipo + intentos fallidos. Notify. */
    val ACCESS_EVENT: UUID = controlUuid("0005")

    /** Alarma sonora (zumbador): 0 apagada, 1 sonando. Read y Write cifrada. */
    val ALARM: UUID = controlUuid("0006")

    /** Segundos entre posiciones del GPS, de 1 a 60. Read y Write cifrada. */
    val GPS_INTERVAL: UUID = controlUuid("0007")

    /** Client Characteristic Configuration Descriptor: activa Notify/Indicate. */
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = sigUuid("2902")

    private fun resolve(override: String, default: UUID): UUID =
        override.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: default
}