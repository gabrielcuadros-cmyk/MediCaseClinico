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
import com.example.medicaseclinico.domain.model.CaseAccess
import com.example.medicaseclinico.domain.model.CaseCapability
import com.example.medicaseclinico.ui.components.NoPermissionScreen
import com.example.medicaseclinico.ui.screens.alerts.AlertsRoute
import com.example.medicaseclinico.ui.screens.dashboard.DashboardPermissions
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
    access: CaseAccess,
    modifier: Modifier = Modifier,
    onSignOut: (() -> Unit)? = null
) {
    val canControlCase = access.can(CaseCapability.CONTROL_CASE)
    val canManageUsers = access.can(CaseCapability.MANAGE_USERS)

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
                // "Ver registro de accesos": aperturas, PIN incorrectos y
                // bloqueos del teclado están en Alertas, bajo el filtro Seguridad.
                onOpenAccessLog = {
                    requestedAlertFilter = AlertFilter.SECURITY
                    navController.navigateToTopLevel(TopLevelDestination.ALERTS)
                },
                permissions = DashboardPermissions.of(access)
            )
        }
        // Cada ruta comprueba el permiso aunque su pestaña o su botón ya estén
        // ocultos: un widget o un atajo podrían abrirla igualmente.
        composable(TopLevelDestination.TEMPERATURE.route) {
            if (access.can(CaseCapability.VIEW_TEMPERATURE)) {
                TemperatureHistoryRoute(
                    onBack = { navController.popBackStack() },
                    canExportReport = access.can(CaseCapability.EXPORT_REPORTS)
                )
            } else {
                NoPermissionScreen()
            }
        }
        composable(TopLevelDestination.MAP.route) {
            if (access.can(CaseCapability.VIEW_LOCATION)) {
                MapRoute(
                    onBack = { navController.popBackStack() },
                    onOpenLossMode = { navController.navigate(SecondaryRoutes.LOSS_MODE) }
                        .takeIf { canControlCase },
                    onConfigureGeofence = { navController.navigate(SecondaryRoutes.GEOFENCE_CONFIG) }
                        .takeIf { canControlCase }
                )
            } else {
                NoPermissionScreen()
            }
        }
        composable(SecondaryRoutes.GEOFENCE_CONFIG) {
            if (canControlCase) {
                GeofenceConfigRoute(onBack = { navController.popBackStack() })
            } else {
                NoPermissionScreen()
            }
        }
        composable(SecondaryRoutes.LOSS_MODE) {
            if (canControlCase) {
                LossModeRoute(onBack = { navController.popBackStack() })
            } else {
                NoPermissionScreen()
            }
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
                onSignOut = onSignOut,
                canControlCase = canControlCase,
                canManageUsers = canManageUsers
            )
        }
        composable(SecondaryRoutes.AUTHORIZED_USERS) {
            if (canManageUsers) {
                AuthorizedUsersRoute(
                    onBack = { navController.popBackStack() },
                    onAddUser = { navController.navigate(SecondaryRoutes.ADD_USER) },
                    onOpenPermissions = { userId ->
                        navController.navigate(SecondaryRoutes.userPermissions(userId))
                    }
                )
            } else {
                NoPermissionScreen()
            }
        }
        composable(SecondaryRoutes.USER_PERMISSIONS) {
            if (canManageUsers) {
                UserPermissionsRoute(
                    onBack = { navController.popBackStack() },
                    onRevoked = { navController.popBackStack() }
                )
            } else {
                NoPermissionScreen()
            }
        }
        composable(SecondaryRoutes.ADD_USER) {
            if (canManageUsers) {
                AddUserRoute(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            } else {
                NoPermissionScreen()
            }
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