package com.example.medicaseclinico.ui.navigation

/**
 * Pantallas que no están en la barra inferior pero pertenecen a una pestaña.
 */
object SecondaryRoutes {
    const val NOTIFICATION_SETTINGS = "notification_settings"

    private val parentTab: Map<String, TopLevelDestination> = mapOf(
        NOTIFICATION_SETTINGS to TopLevelDestination.SECURITY
    )

    fun parentOf(route: String?): TopLevelDestination? = route?.let { parentTab[it] }
}