package com.example.medicaseclinico.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.medicaseclinico.domain.model.AlertFilter
import com.example.medicaseclinico.ui.screens.alerts.AlertsRoute
import com.example.medicaseclinico.ui.screens.dashboard.DashboardRoute
import com.example.medicaseclinico.ui.screens.dashboard.DashboardSection
import com.example.medicaseclinico.ui.screens.geofence.GeofenceConfigRoute
import com.example.medicaseclinico.ui.screens.history.TemperatureHistoryRoute
import com.example.medicaseclinico.ui.screens.lossmode.LossModeRoute
import com.example.medicaseclinico.ui.screens.map.MapRoute
import com.example.medicaseclinico.ui.screens.notifications.NotificationSettingsRoute
import com.example.medicaseclinico.ui.screens.security.SecurityRoute
import com.example.medicaseclinico.ui.screens.users.AuthorizedUsersRoute
import com.example.medicaseclinico.ui.screens.users.add.AddUserRoute
import com.example.medicaseclinico.ui.screens.users.permissions.UserPermissionsRoute

/**
 * Grafo de navegación principal de MediCase Clínico.
 *
 * Desacoplado: recibe el NavController desde fuera (lo crea la raíz de la app),
 * y cada pantalla solo se registra aquí. Ninguna pantalla importa a otra.
 *
 * @param onSignOut cierra la sesión, o null si esta instalación no usa cuentas.
 */
@Composable
fun MediCaseNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onSignOut: (() -> Unit)? = null
) {
    // Filtro con el que el Inicio pide abrir Alertas. Vive aquí porque es lo
    // único que conoce a las dos pantallas; se borra en cuanto se aplica.
    var requestedAlertFilter by rememberSaveable { mutableStateOf<AlertFilter?>(null) }

    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier
    ) {
        composable(TopLevelDestination.HOME.route) {
            DashboardRoute(
                onOpenLossMode = { navController.navigate(SecondaryRoutes.LOSS_MODE) },
                // Los atajos equivalen a tocar la pestaña en la barra inferior.
                onOpenSection = { section ->
                    navController.navigateToTopLevel(
                        when (section) {
                            DashboardSection.HISTORY -> TopLevelDestination.TEMPERATURE
                            DashboardSection.LOCATION -> TopLevelDestination.MAP
                            DashboardSection.ALERTS -> TopLevelDestination.ALERTS
                            DashboardSection.SETTINGS -> TopLevelDestination.SECURITY
                        }
                    )
                },
                // "Registrar Acceso por PIN": los intentos del teclado del
                // estuche están en Alertas, bajo el filtro Seguridad.
                onOpenAccessLog = {
                    requestedAlertFilter = AlertFilter.SECURITY
                    navController.navigateToTopLevel(TopLevelDestination.ALERTS)
                }
            )
        }
        composable(TopLevelDestination.TEMPERATURE.route) {
            TemperatureHistoryRoute(onBack = { navController.popBackStack() })
        }
        composable(TopLevelDestination.MAP.route) {
            MapRoute(
                onBack = { navController.popBackStack() },
                onOpenLossMode = { navController.navigate(SecondaryRoutes.LOSS_MODE) },
                onConfigureGeofence = { navController.navigate(SecondaryRoutes.GEOFENCE_CONFIG) }
            )
        }
        composable(SecondaryRoutes.GEOFENCE_CONFIG) {
            GeofenceConfigRoute(onBack = { navController.popBackStack() })
        }
        composable(SecondaryRoutes.LOSS_MODE) {
            LossModeRoute(onBack = { navController.popBackStack() })
        }
        composable(TopLevelDestination.ALERTS.route) {
            AlertsRoute(
                onBack = { navController.popBackStack() },
                requestedFilter = requestedAlertFilter,
                onRequestedFilterApplied = { requestedAlertFilter = null }
            )
        }
        composable(TopLevelDestination.SECURITY.route) {
            SecurityRoute(
                onOpenUsers = { navController.navigate(SecondaryRoutes.AUTHORIZED_USERS) },
                onAddUser = { navController.navigate(SecondaryRoutes.ADD_USER) },
                onOpenNotifications = { navController.navigate(SecondaryRoutes.NOTIFICATION_SETTINGS) },
                onSignOut = onSignOut
            )
        }
        composable(SecondaryRoutes.AUTHORIZED_USERS) {
            AuthorizedUsersRoute(
                onBack = { navController.popBackStack() },
                onAddUser = { navController.navigate(SecondaryRoutes.ADD_USER) },
                onOpenPermissions = { userId ->
                    navController.navigate(SecondaryRoutes.userPermissions(userId))
                }
            )
        }
        composable(SecondaryRoutes.USER_PERMISSIONS) {
            UserPermissionsRoute(
                onBack = { navController.popBackStack() },
                onRevoked = { navController.popBackStack() }
            )
        }
        composable(SecondaryRoutes.ADD_USER) {
            AddUserRoute(
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() }
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