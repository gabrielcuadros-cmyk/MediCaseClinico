package com.example.medicaseclinico.domain.model

/**
 * Lectura térmica del estuche tal como la entiende la lógica de negocio.
 *
 * No tiene anotaciones de Room: si mañana el historial viene de Firebase o
 * directamente del BLE, el ViewModel y la UI no cambian.
 */
data class TemperatureReading(
    val id: Long = 0L,
    val temperatureCelsius: Float,
    val timestamp: Long,
    val isAlertTriggered: Boolean,
    val batteryLevel: Int? = null
)