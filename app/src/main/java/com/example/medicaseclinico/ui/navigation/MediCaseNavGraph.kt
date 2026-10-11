package com.example.medicaseclinico.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.medicaseclinico.R
import com.example.medicaseclinico.ui.components.PendingFeatureScreen
import com.example.medicaseclinico.ui.screens.dashboard.DashboardRoute
import com.example.medicaseclinico.ui.screens.history.TemperatureHistoryRoute
import com.example.medicaseclinico.ui.screens.notifications.NotificationSettingsRoute

/**
 * Grafo de navegación principal de MediCase Clínico.
 *
 * Desacoplado: recibe el NavController desde fuera (lo crea la raíz de la app),
 * y cada pantalla solo se registra aquí. Ninguna pantalla importa a otra.
 */
@Composable
fun MediCaseNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier
    ) {
        composable(TopLevelDestination.HOME.route) {
            DashboardRoute()
        }
        composable(TopLevelDestination.TEMPERATURE.route) {
            TemperatureHistoryRoute(onBack = { navController.popBackStack() })
        }
        composable(TopLevelDestination.MAP.route) {
            PendingFeatureScreen(titleRes = TopLevelDestination.MAP.labelRes)
        }
        composable(TopLevelDestination.ALERTS.route) {
            PendingFeatureScreen(titleRes = TopLevelDestination.ALERTS.labelRes)
        }
        composable(TopLevelDestination.SECURITY.route) {
            PendingFeatureScreen(
                titleRes = TopLevelDestination.SECURITY.labelRes,
                actionLabelRes = R.string.open_notification_settings,
                onAction = { navController.navigate(SecondaryRoutes.NOTIFICATION_SETTINGS) }
            )
        }
        composable(SecondaryRoutes.NOTIFICATION_SETTINGS) {
            NotificationSettingsRoute(onBack = { navController.popBackStack() })
        }
    }
}

/**
 * Navegación entre pestañas de la barra inferior con el comportamiento
 * estándar de Material:
 *  - popUpTo(inicio) + saveState: no se apilan pestañas infinitamente.
 *  - launchSingleTop: tocar la pestaña activa no crea otra copia.
 *  - restoreState: al volver a una pestaña, conserva su scroll y estado.
 */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}