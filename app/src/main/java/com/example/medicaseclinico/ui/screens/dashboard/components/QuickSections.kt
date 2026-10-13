package com.example.medicaseclinico.ui.screens.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.RelativeTime
import com.example.medicaseclinico.ui.components.relativeTimeLabel
import com.example.medicaseclinico.ui.screens.dashboard.DashboardSection
import com.example.medicaseclinico.ui.screens.dashboard.PinLockStatus
import com.example.medicaseclinico.ui.screens.dashboard.QuickSectionsState
import com.example.medicaseclinico.ui.theme.MediCaseTheme

private val CardSpacing = 12.dp

/**
 * "Secciones rápidas" del Dashboard: cuatro atajos a las demás pestañas, cada
 * uno con un dato real de lo que hay dentro.
 */
@Composable
fun QuickSections(
    state: QuickSectionsState,
    onSectionClick: (DashboardSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CardSpacing)) {
        Text(
            text = stringResource(R.string.quick_sections_header),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        QuickRow {
            QuickCard(
                title = stringResource(R.string.quick_history_title),
                description = stringResource(R.string.quick_history_description),
                status = stringResource(R.string.history_range_24h),
                icon = Icons.Outlined.Thermostat,
                onClick = { onSectionClick(DashboardSection.HISTORY) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            QuickCard(
                title = stringResource(R.string.quick_location_title),
                description = stringResource(R.string.quick_location_description),
                // Sin ninguna posición del estuche no se inventa una hora.
                status = state.lastLocationElapsed?.let { relativeTimeLabel(it) }
                    ?: stringResource(R.string.map_empty_no_signal_title),
                icon = Icons.Outlined.LocationOn,
                onClick = { onSectionClick(DashboardSection.LOCATION) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        QuickRow {
            QuickCard(
                title = stringResource(R.string.quick_alerts_title),
                description = stringResource(R.string.quick_alerts_description),
                status = if (state.unreadAlerts > 0) {
                    pluralStringResource(
                        R.plurals.quick_alerts_unread,
                        state.unreadAlerts,
                        state.unreadAlerts
                    )
                } else {
                    stringResource(R.string.quick_alerts_none)
                },
                icon = Icons.Outlined.ErrorOutline,
                onClick = { onSectionClick(DashboardSection.ALERTS) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            QuickCard(
                title = stringResource(R.string.quick_settings_title),
                description = stringResource(R.string.quick_settings_description),
                status = stringResource(
                    when (state.pinLock) {
                        PinLockStatus.ENABLED -> R.string.quick_settings_pin_enabled
                        PinLockStatus.DISABLED -> R.string.quick_settings_pin_disabled
                        PinLockStatus.UNKNOWN -> R.string.value_unknown
                    }
                ),
                icon = Icons.Outlined.Settings,
                onClick = { onSectionClick(DashboardSection.SETTINGS) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

/** Dos tarjetas lado a lado, a la misma altura aunque sus textos ocupen distinto. */
@Composable
private fun QuickRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(CardSpacing),
        content = content
    )
}

@Composable
private fun QuickCard(
    title: String,
    description: String,
    status: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        // Un solo botón para los lectores de pantalla, con sus tres textos.
        modifier = modifier.semantics(mergeDescendants = true) { role = Role.Button },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(showBackground = true, name = "Secciones rápidas")
@Composable
private fun QuickSectionsPreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            QuickSections(
                state = QuickSectionsState(
                    lastLocationElapsed = RelativeTime.Minutes(2),
                    unreadAlerts = 0,
                    pinLock = PinLockStatus.ENABLED
                ),
                onSectionClick = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
