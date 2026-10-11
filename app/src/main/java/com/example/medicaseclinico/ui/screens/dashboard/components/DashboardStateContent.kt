package com.example.medicaseclinico.ui.screens.dashboard.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.example.medicaseclinico.R
import com.example.medicaseclinico.ui.components.MediCaseLoadingState
import com.example.medicaseclinico.ui.components.MediCaseMessageState
import com.example.medicaseclinico.ui.screens.dashboard.ErrorReason
import com.example.medicaseclinico.ui.theme.MediCaseTheme

/** Carga inicial del Dashboard (DashboardUiState.Loading). */
@Composable
fun DashboardLoadingContent(modifier: Modifier = Modifier) {
    MediCaseLoadingState(
        message = stringResource(R.string.dashboard_loading),
        modifier = modifier
    )
}

/** Sin lecturas registradas (DashboardUiState.Empty). */
@Composable
fun DashboardEmptyContent(modifier: Modifier = Modifier) {
    MediCaseMessageState(
        icon = Icons.Outlined.DeviceThermostat,
        title = stringResource(R.string.dashboard_empty_title),
        message = stringResource(R.string.dashboard_empty_message),
        accentColor = MaterialTheme.colorScheme.primary,
        accentContainerColor = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
    )
}

/** Error controlado (DashboardUiState.Error). */
@Composable
fun DashboardErrorContent(
    reason: ErrorReason,
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = reason.errorStyle()
    MediCaseMessageState(
        icon = style.icon,
        title = stringResource(style.titleRes),
        message = stringResource(style.messageRes),
        accentColor = MediCaseTheme.statusColors.critical,
        accentContainerColor = MediCaseTheme.statusColors.criticalContainer,
        actionLabel = if (canRetry) stringResource(R.string.action_retry) else null,
        onAction = if (canRetry) onRetry else null,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Assertive }
    )
}

// ------------------------------------------------------------------
//  Mapeo ErrorReason → presentación (solo capa UI)
// ------------------------------------------------------------------
private data class DashboardErrorStyle(
    val icon: ImageVector,
    @get:StringRes val titleRes: Int,
    @get:StringRes val messageRes: Int
)

private fun ErrorReason.errorStyle(): DashboardErrorStyle = when (this) {
    ErrorReason.DATABASE -> DashboardErrorStyle(
        icon = Icons.Outlined.Storage,
        titleRes = R.string.error_database_title,
        messageRes = R.string.error_database_message
    )
    ErrorReason.BLUETOOTH -> DashboardErrorStyle(
        icon = Icons.Filled.BluetoothDisabled,
        titleRes = R.string.error_bluetooth_title,
        messageRes = R.string.error_bluetooth_message
    )
    ErrorReason.NETWORK -> DashboardErrorStyle(
        icon = Icons.Outlined.CloudOff,
        titleRes = R.string.error_network_title,
        messageRes = R.string.error_network_message
    )
    ErrorReason.UNKNOWN -> DashboardErrorStyle(
        icon = Icons.Outlined.ErrorOutline,
        titleRes = R.string.error_unknown_title,
        messageRes = R.string.error_unknown_message
    )
}

// ------------------------------------------------------------------
//  Vistas previas
// ------------------------------------------------------------------
@Preview(showBackground = true, name = "Dashboard - Cargando")
@Composable
private fun DashboardLoadingPreview() {
    Surface {
        DashboardLoadingContent()
    }
}

@Preview(showBackground = true, name = "Dashboard - Vacío")
@Composable
private fun DashboardEmptyPreview() {
    Surface {
        DashboardEmptyContent()
    }
}

@Preview(showBackground = true, name = "Dashboard - Error Bluetooth")
@Composable
private fun DashboardErrorPreview() {
    Surface {
        DashboardErrorContent(reason = ErrorReason.BLUETOOTH, canRetry = true, onRetry = {})
    }
}