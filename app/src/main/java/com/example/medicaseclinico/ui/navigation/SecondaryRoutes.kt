package com.example.medicaseclinico.ui.navigation

/**
 * Pantallas que no están en la barra inferior pero pertenecen a una pestaña.
 */
object SecondaryRoutes {
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val GEOFENCE_CONFIG = "geofence_config"
    const val LOSS_MODE = "loss_mode"
    const val ADD_USER = "add_user"
    const val AUTHORIZED_USERS = "authorized_users"

    /** Argumento de [USER_PERMISSIONS]: id del registro de la persona. */
    const val USER_ID_ARG = "userId"
    const val USER_PERMISSIONS = "user_permissions/{$USER_ID_ARG}"

    fun userPermissions(userId: String): String = "user_permissions/$userId"

    private val parentTab: Map<String, TopLevelDestination> = mapOf(
        AUTHORIZED_USERS to TopLevelDestination.SECURITY,
        USER_PERMISSIONS to TopLevelDestination.SECURITY,
        NOTIFICATION_SETTINGS to TopLevelDestination.SECURITY,
        GEOFENCE_CONFIG to TopLevelDestination.MAP,
        LOSS_MODE to TopLevelDestination.MAP,
        ADD_USER to TopLevelDestination.SECURITY
    )

    fun parentOf(route: String?): TopLevelDestination? = route?.let { parentTab[it] }
}