package com.example.medicaseclinico.domain.model

/** Tipos de alerta de MediCase, uno por cada interruptor del diseño. */
enum class AlertType {
    TEMPERATURE,
    BATTERY,
    CONNECTION,
    GEOFENCE,
    SECURITY
}

/**
 * Preferencias de alertas del usuario (pantalla "Notificaciones").
 */
data class NotificationPreferences(
    val temperatureAlertsEnabled: Boolean = true,
    val batteryAlertsEnabled: Boolean = true,
    val connectionAlertsEnabled: Boolean = true,
    val geofenceAlertsEnabled: Boolean = true,
    val securityAlertsEnabled: Boolean = true,
    val temporarilySilenced: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStartMinute: Int = 22 * 60,
    val quietHoursEndMinute: Int = 7 * 60
) {
    /**
     * Decide si una alerta debe mostrarse.
     */
    fun allows(type: AlertType, minuteOfDay: Int): Boolean {
        if (!isTypeEnabled(type)) return false
        if (type == AlertType.TEMPERATURE) return true
        val silenced = temporarilySilenced ||
                (quietHoursEnabled && isWithinQuietHours(minuteOfDay))
        return !silenced
    }

    fun isTypeEnabled(type: AlertType): Boolean = when (type) {
        AlertType.TEMPERATURE -> temperatureAlertsEnabled
        AlertType.BATTERY -> batteryAlertsEnabled
        AlertType.CONNECTION -> connectionAlertsEnabled
        AlertType.GEOFENCE -> geofenceAlertsEnabled
        AlertType.SECURITY -> securityAlertsEnabled
    }

    /**
     * Soporta horarios que cruzan la medianoche (22:00 -> 07:00).
     */
    fun isWithinQuietHours(minuteOfDay: Int): Boolean {
        val start = quietHoursStartMinute
        val end = quietHoursEndMinute
        return when {
            start == end -> false
            start < end -> minuteOfDay in start until end
            else -> minuteOfDay >= start || minuteOfDay < end
        }
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}