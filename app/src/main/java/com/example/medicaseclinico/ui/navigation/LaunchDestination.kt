package com.example.medicaseclinico.ui.navigation

import android.content.Intent

/**
 * Pestaña que se pide abrir desde fuera de la app (los accesos directos del
 * widget de la pantalla de inicio).
 *
 * El Intent lleva la ruta de la pestaña como extra, nunca una pantalla
 * secundaria: así un Intent ajeno no puede saltarse la navegación de la app
 * ni llegar a una pantalla que espera argumentos.
 */
object LaunchDestination {
    /** Extra con la ruta de una [TopLevelDestination] (p. ej. "map"). */
    const val EXTRA_ROUTE = "com.example.medicaseclinico.extra.ROUTE"

    /** Pestaña pedida por [intent], o null si no pide ninguna o la ruta no existe. */
    fun from(intent: Intent?): TopLevelDestination? {
        val route = intent?.getStringExtra(EXTRA_ROUTE) ?: return null
        return TopLevelDestination.entries.firstOrNull { it.route == route }
    }
}
