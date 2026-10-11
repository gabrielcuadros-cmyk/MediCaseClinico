package com.example.medicaseclinico.ui.navigation

import androidx.annotation.StringRes
import com.example.medicaseclinico.R

/**
 * Los 5 destinos fijos de la barra inferior, en el orden exacto del diseño:
 * Inicio · Temp · Mapa · Alertas · Seguridad.
 */
enum class TopLevelDestination(
    val route: String,
    @get:StringRes val labelRes: Int
) {
    HOME(route = "home", labelRes = R.string.nav_home),
    TEMPERATURE(route = "temperature", labelRes = R.string.nav_temperature),
    MAP(route = "map", labelRes = R.string.nav_map),
    ALERTS(route = "alerts", labelRes = R.string.nav_alerts),
    SECURITY(route = "security", labelRes = R.string.nav_security);

    companion object {
        val START = HOME

        /** Resuelve la pestaña activa, incluidas las pantallas secundarias. */
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route } ?: SecondaryRoutes.parentOf(route)
    }
}