package com.example.medicaseclinico.data.mqtt

import com.example.medicaseclinico.BuildConfig

/**
 * Conexión con el broker MQTT al que publica el estuche (ver hardware/mqtt-protocol.md).
 *
 * Se fija al compilar desde local.properties: el usuario de la app nunca la ve ni la
 * edita. Si falta o es inválida, [fromBuildConfig] devuelve null y la app sigue por
 * Bluetooth, en lugar de intentar conectarse a un broker mal escrito.
 *
 * @param topicPrefix prefijo de los topics, sin barras al inicio ni al final.
 * @param username usuario del broker, o null si no pide autenticación.
 * @param password contraseña del broker, o null si no la pide.
 * @param caseId identificador del estuche en un broker compartido por varios, o null
 * si los topics van directo bajo el prefijo.
 */
data class MqttConfig(
    val host: String,
    val port: Int,
    val useTls: Boolean,
    val topicPrefix: String,
    val username: String?,
    val password: String?,
    val caseId: String?
) {
    /** Raíz común de todos los topics del estuche: `<prefijo>` o `<prefijo>/<id>`. */
    val topicBase: String = if (caseId == null) topicPrefix else "$topicPrefix/$caseId"

    /** Topic completo de [name], p. ej. `topic("temperatura")`. */
    fun topic(name: String): String = "$topicBase/$name"

    /** Sin la contraseña: este objeto puede acabar en un log o en un mensaje de error. */
    override fun toString(): String =
        "MqttConfig(host=$host, port=$port, useTls=$useTls, topicBase=$topicBase, " +
            "username=${username ?: "-"}, password=${if (password == null) "-" else "***"})"

    companion object {
        /** Puerto estándar de MQTT sobre TCP. */
        const val DEFAULT_PORT = 1883

        /** Puerto estándar de MQTT sobre TLS. */
        const val DEFAULT_TLS_PORT = 8883

        private const val MAX_PORT = 65_535

        /** Configuración del estuche según local.properties, o null si no hay MQTT. */
        fun fromBuildConfig(): MqttConfig? = parse(
            host = BuildConfig.MQTT_HOST,
            port = BuildConfig.MQTT_PORT,
            useTls = BuildConfig.MQTT_USE_TLS,
            topicPrefix = BuildConfig.MQTT_TOPIC_PREFIX,
            username = BuildConfig.MQTT_USERNAME,
            password = BuildConfig.MQTT_PASSWORD,
            caseId = BuildConfig.MQTT_CASE_ID
        )

        /**
         * Valida los valores en texto tal como llegan de local.properties.
         *
         * Devuelve null (sin MQTT) si falta el host o el prefijo, o si algún valor es
         * inválido: un puerto fuera de rango, o comodines `+`/`#` en el prefijo o el
         * id, que harían a la app suscribirse a topics de otros.
         */
        fun parse(
            host: String,
            port: String,
            useTls: String,
            topicPrefix: String,
            username: String,
            password: String,
            caseId: String
        ): MqttConfig? {
            val cleanHost = host.trim()
            if (cleanHost.isEmpty() || cleanHost.any { it.isWhitespace() }) return null

            val tls = when (useTls.trim().lowercase()) {
                "", "false" -> false
                "true" -> true
                else -> return null
            }

            val cleanPort = port.trim()
            val portNumber = if (cleanPort.isEmpty()) {
                if (tls) DEFAULT_TLS_PORT else DEFAULT_PORT
            } else {
                cleanPort.toIntOrNull()?.takeIf { it in 1..MAX_PORT } ?: return null
            }

            val prefix = topicPrefix.trim().trim('/')
            if (prefix.isEmpty() || !isValidTopicLevel(prefix)) return null

            val id = caseId.trim().takeIf { it.isNotEmpty() }
            if (id != null && (id.contains('/') || !isValidTopicLevel(id))) return null

            return MqttConfig(
                host = cleanHost,
                port = portNumber,
                useTls = tls,
                topicPrefix = prefix,
                username = username.trim().takeIf { it.isNotEmpty() },
                password = password.takeIf { it.isNotEmpty() },
                caseId = id
            )
        }

        /** Sin comodines ni niveles vacíos (`a//b`), que MQTT no admite al publicar. */
        private fun isValidTopicLevel(value: String): Boolean =
            value.none { it == '+' || it == '#' || it.isWhitespace() } &&
                value.split('/').none { it.isEmpty() }
    }
}
