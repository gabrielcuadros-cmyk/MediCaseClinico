package com.example.medicaseclinico.data.mqtt

import java.security.SecureRandom
import java.util.Random

/**
 * Client ID con el que la app se presenta al broker.
 *
 * El broker desconecta a un cliente cuando entra otro con el mismo ID. Por eso no se
 * escribe a mano ni se comparte con el estuche (`mcesp32<MAC>`): cada instalación
 * genera el suyo una sola vez, y así dos teléfonos del mismo paciente tampoco se
 * echan entre sí.
 *
 * Formato `mcapp` + 16 dígitos hex (21 caracteres): MQTT 3.1.1 solo obliga a los
 * brokers a aceptar IDs de hasta 23 caracteres y únicamente con letras y dígitos,
 * así que sin guiones ni UUID completos funciona con cualquiera.
 */
object MqttClientId {

    /** Prefijo de la app; el del estuche es `mcesp32` (ver hardware/mqtt-protocol.md). */
    const val APP_PREFIX = "mcapp"

    /** Longitud máxima que todo broker MQTT 3.1.1 está obligado a aceptar. */
    const val MAX_PORTABLE_LENGTH = 23

    // 8 bytes = 64 bits aleatorios: una colisión entre instalaciones es despreciable.
    private const val RANDOM_BYTES = 8

    private val APP_CLIENT_ID = Regex("^$APP_PREFIX[0-9a-f]{${RANDOM_BYTES * 2}}$")

    private val secureRandom = SecureRandom()

    /** Un ID nuevo, aleatorio: `mcapp` + 16 dígitos hex en minúsculas. */
    fun newAppClientId(random: Random = secureRandom): String {
        val bytes = ByteArray(RANDOM_BYTES).also { random.nextBytes(it) }
        return APP_PREFIX + bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    /**
     * true si [value] es un ID de la app bien formado. Lo que se lee del disco se
     * valida: un valor dañado, o uno del formato largo anterior, se reemplaza por uno
     * nuevo en lugar de usarse.
     */
    fun isValidAppClientId(value: String): Boolean = APP_CLIENT_ID.matches(value)
}
