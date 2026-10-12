package com.example.medicaseclinico.domain.alert

import com.example.medicaseclinico.domain.model.Geofence
import com.example.medicaseclinico.domain.model.TemperatureStatus

/**
 * Recibe eventos del estuche y decide si el usuario debe ser alertado.
 */
interface AlertPublisher {
    /** Cada lectura de temperatura recibida del estuche. */
    fun onTemperature(celsius: Float, status: TemperatureStatus)

    /** Cada nivel de batería recibido del estuche (0–100). */
    fun onBatteryLevel(percent: Int)

    /** El enlace BLE pasó a desconectado. */
    fun onConnectionLost()

    /** El enlace BLE volvió a establecerse. */
    fun onConnectionRestored()

    /** El estuche salió de una zona de seguridad activa. */
    fun onGeofenceExited(geofence: Geofence)

    /** El estuche volvió a entrar en una zona de la que había salido. */
    fun onGeofenceEntered(geofence: Geofence)
}