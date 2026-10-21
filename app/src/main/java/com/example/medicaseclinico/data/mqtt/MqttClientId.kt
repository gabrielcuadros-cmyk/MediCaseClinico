package com.example.medicaseclinico.data.mqtt

import java.util.UUID

/**
 * Client ID con el que la app se presenta al broker.
 *
 * El broker desconecta a un cliente cuando entra otro con el mismo ID. Por eso no se
 * escribe a mano ni se comparte con el estuche (`medicase-esp32-<MAC>`): cada
 * instalación genera el suyo una sola vez, y así dos teléfonos del mismo paciente
 * tampoco se echan entre sí.
 */
object MqttClientId {

    /** Prefijo de la app; el del estuche es `medicase-esp32-` (ver hardware/mqtt-protocol.md). */
    const val APP_PREFIX = "medicase-app-"

    private val APP_CLIENT_ID = Regex(
        "^" + Regex.escape(APP_PREFIX) +
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )

    /** Un ID nuevo, aleatorio: `medicase-app-<UUID>`. */
    fun newAppClientId(uuid: UUID = UUID.randomUUID()): String = APP_PREFIX + uuid

    /**
     * true si [value] es un ID de la app bien formado. Lo que se lee del disco se
     * valida: un valor dañado se reemplaza por uno nuevo en lugar de usarse.
     */
    fun isValidAppClientId(value: String): Boolean = APP_CLIENT_ID.matches(value)
}
