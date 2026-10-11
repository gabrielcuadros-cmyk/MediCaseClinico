package com.example.medicaseclinico.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.medicaseclinico.ui.theme.MediCaseTheme

/**
 * Barra de navegación inferior de MediCase Clínico con los 5 destinos fijos:
 * Inicio · Temp · Mapa · Alertas · Seguridad.
 *
 * Componente sin estado (stateless): no conoce al NavController.
 * Recibe qué pestaña está activa y avisa con [onNavigate] cuál se tocó.
 * Así se puede previsualizar y testear de forma aislada.
 *
 * @param currentDestination pestaña activa (se pinta en Azul Eléctrico).
 * @param onNavigate callback al tocar una pestaña.
 * @param activeAlertsCount alertas sin revisar; si es > 0 se muestra un indicador rojo.
 */
@Composable
fun MediCaseNavigationBar(
    currentDestination: TopLevelDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    activeAlertsCount: Int = 0
) {
    // Línea superior fina que separa la barra del contenido, como en el diseño.
    Column(modifier = modifier) {
        HorizontalDivider(
            thickness = NAV_BAR_BORDER_THICKNESS,
            color = MaterialTheme.colorScheme.outline
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = NAV_BAR_ELEVATION
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val isSelected = destination == currentDestination
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onNavigate(destination) },
                    icon = {
                        NavigationIcon(
                            destination = destination,
                            isSelected = isSelected,
                            activeAlertsCount = activeAlertsCount
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(destination.labelRes),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        // El diseño marca la pestaña activa solo con color, sin "píldora".
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

/** Ícono de cada pestaña, con indicador rojo en "Alertas" si hay alertas activas. */
@Composable
private fun NavigationIcon(
    destination: TopLevelDestination,
    isSelected: Boolean,
    activeAlertsCount: Int
) {
    val icon = if (isSelected) destination.selectedIcon else destination.unselectedIcon
    if (destination == TopLevelDestination.ALERTS && activeAlertsCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(text = formatBadgeCount(activeAlertsCount))
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = null)
        }
    } else {
        Icon(imageVector = icon, contentDescription = null)
    }
}

/** "99+" para no desbordar el indicador con cifras grandes. */
private fun formatBadgeCount(count: Int): String =
    if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else count.toString()

// ------------------------------------------------------------------
//  Íconos por destino (relleno = activo, contorno = inactivo).
// ------------------------------------------------------------------
private val TopLevelDestination.selectedIcon: ImageVector
    get() = when (this) {
        TopLevelDestination.HOME -> Icons.Filled.Home
        TopLevelDestination.TEMPERATURE -> Icons.Filled.DeviceThermostat
        TopLevelDestination.MAP -> Icons.Filled.LocationOn
        TopLevelDestination.ALERTS -> Icons.Filled.Error
        TopLevelDestination.SECURITY -> Icons.Filled.ManageAccounts
    }

private val TopLevelDestination.unselectedIcon: ImageVector
    get() = when (this) {
        TopLevelDestination.HOME -> Icons.Outlined.Home
        TopLevelDestination.TEMPERATURE -> Icons.Outlined.DeviceThermostat
        TopLevelDestination.MAP -> Icons.Outlined.LocationOn
        TopLevelDestination.ALERTS -> Icons.Outlined.ErrorOutline
        TopLevelDestination.SECURITY -> Icons.Outlined.ManageAccounts
    }

// ------------------------------------------------------------------
//  Conexión con el NavController
// ------------------------------------------------------------------
/**
 * Observa la ruta actual del NavController y la traduce a un destino de primer nivel.
 */
@Composable
fun NavHostController.currentTopLevelDestination(): TopLevelDestination? {
    val backStackEntry by currentBackStackEntryAsState()
    return TopLevelDestination.fromRoute(backStackEntry?.destination?.route)
}

private val NAV_BAR_BORDER_THICKNESS = Dp(1f)
private val NAV_BAR_ELEVATION = Dp(0f)
private const val MAX_BADGE_COUNT = 99

// ------------------------------------------------------------------
//  Vista previa
// ------------------------------------------------------------------
@Preview(showBackground = true, name = "Barra inferior - Inicio activo")
@Composable
private fun MediCaseNavigationBarPreview() {
    MediCaseTheme {
        MediCaseNavigationBar(
            currentDestination = TopLevelDestination.HOME,
            onNavigate = {}
        )
    }
}