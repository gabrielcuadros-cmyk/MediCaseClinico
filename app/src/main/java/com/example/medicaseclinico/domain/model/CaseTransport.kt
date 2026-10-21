package com.example.medicaseclinico.domain.model

/**
 * Por dónde llegan los datos del estuche.
 *
 * Se fija al compilar (ver `MqttConfig`): con un broker configurado, MQTT; si no,
 * Bluetooth de respaldo. La UI lo usa solo para explicar lo que pasa con las palabras
 * correctas ("vía MQTT", no "acércate al estuche"); la lógica no depende de él.
 */
enum class CaseTransport {
    /** Enlace directo con el estuche por Bluetooth LE: hay que estar cerca. */
    BLUETOOTH,

    /** El estuche publica por Wi-Fi en un broker MQTT: funciona a distancia. */
    MQTT
}
