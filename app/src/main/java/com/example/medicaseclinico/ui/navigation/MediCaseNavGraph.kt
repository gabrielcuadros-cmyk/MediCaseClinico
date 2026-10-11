package com.example.medicaseclinico.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.medicaseclinico.ui.components.PendingFeatureScreen

/**
 * Grafo de navegación principal de MediCase Clínico.
 *
 * Desacoplado: recibe el NavController desde fuera (lo crea la raíz de la app),
 * y cada pantalla solo se registra aquí. Ninguna pantalla importa a otra.
 *
 * Los destinos con PendingFeatureScreen se reemplazarán por sus pantallas
 * reales (el Dashboard en el Commit 21).
 */
@Composable
fun MediCaseNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.START.route,
        modifier = modifier
    ) {
        composable(TopLevelDestination.HOME.route) {
            PendingFeatureScreen(titleRes = TopLevelDestination.HOME.labelRes)
        }
        composable(TopLevelDestination.TEMPERATURE.route) {
            PendingFeatureScreen(titleRes = TopLevelDestination.TEMPERATURE.labelRes)
        }
        composable(TopLevelDestination.MAP.route) {
            PendingFeatureScreen(titleRes = TopLevelDestination.MAP.labelRes)
        }
        composable(TopLevelDestination.ALERTS.route) {
            PendingFeatureScreen(titleRes = TopLevelDestination.ALERTS.labelRes)
        }
        composable(TopLevelDestination.SECURITY.route) {
            PendingFeatureScreen(titleRes = TopLevelDestination.SECURITY.labelRes)
        }
    }
}

/**
 * Navegación entre pestañas de la barra inferior con el comportamiento
 * estándar de Material:
 *  - popUpTo(inicio) + saveState: no se apilan pestañas infinitamente
 *    al tocarlas una y otra vez.
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