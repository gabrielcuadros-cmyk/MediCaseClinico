package com.example.medicaseclinico.ui.navigation

/**
 * Pantallas que no están en la barra inferior pero pertenecen a una pestaña.
 */
object SecondaryRoutes {
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val GEOFENCE_CONFIG = "geofence_config"
    const val LOSS_MODE = "loss_mode"

    private val parentTab: Map<String, TopLevelDestination> = mapOf(
        NOTIFICATION_SETTINGS to TopLevelDestination.SECURITY,
        GEOFENCE_CONFIG to TopLevelDestination.MAP,
        LOSS_MODE to TopLevelDestination.MAP
    )

    fun parentOf(route: String?): TopLevelDestination? = route?.let { parentTab[it] }
}