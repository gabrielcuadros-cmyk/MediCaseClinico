package com.example.medicaseclinico.ui.screens.alerts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.AlertType
import com.example.medicaseclinico.domain.model.RelativeTime
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.ui.components.relativeTimeLabel
import com.example.medicaseclinico.ui.screens.alerts.AlertItem
import com.example.medicaseclinico.ui.theme.MediCaseTheme

private val CardShape = RoundedCornerShape(14.dp)
private val SideBarWidth = 4.dp
private val IconBoxSize = 28.dp

/**
 * Tarjeta de una alerta: barra lateral del color de su gravedad, ícono,
 * título, tiempo relativo a la derecha y descripción.
 *
 * El color nunca es la única pista: cada tipo tiene también ícono y título
 * propios. Los lectores de pantalla leen la tarjeta entera como un elemento.
 */
@Composable
fun AlertCard(
    alert: AlertItem,
    modifier: Modifier = Modifier
) {
    val style = alert.cardStyle()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(SideBarWidth)
                    .fillMaxHeight()
                    .background(style.accent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = style.container,
                        modifier = Modifier.size(IconBoxSize)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = style.icon,
                                contentDescription = null,
                                tint = style.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = style.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = relativeTimeLabel(alert.elapsed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = style.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class AlertCardStyle(
    val icon: ImageVector,
    val accent: Color,
    val container: Color,
    val title: String,
    val description: String
)

/**
 * Colores del diseño: rojo para lo que pone en riesgo la insulina o el acceso,
 * ámbar para lo que pide atención y azul para avisos de diagnóstico.
 */
@Composable
private fun AlertItem.cardStyle(): AlertCardStyle {
    val statusColors = MediCaseTheme.statusColors
    return when (type) {
        AlertType.TEMPERATURE -> AlertCardStyle(
            icon = Icons.Outlined.DeviceThermostat,
            accent = statusColors.critical,
            container = statusColors.criticalContainer,
            title = stringResource(R.string.alerts_temperature_title),
            description = temperatureDescription(value)
        )
        AlertType.BATTERY -> AlertCardStyle(
            icon = Icons.Outlined.BatteryAlert,
            accent = statusColors.warning,
            container = statusColors.warningContainer,
            title = if (value != null && !value.isNaN()) {
                stringResource(R.string.alerts_battery_title_with_level, value.toInt())
            } else {
                stringResource(R.string.alerts_battery_title)
            },
            description = stringResource(R.string.alerts_battery_description)
        )
        AlertType.CONNECTION -> AlertCardStyle(
            icon = Icons.Outlined.Bluetooth,
            accent = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.primaryContainer,
            title = stringResource(R.string.alerts_connection_title),
            description = stringResource(R.string.alerts_connection_description)
        )
        AlertType.GEOFENCE -> AlertCardStyle(
            icon = Icons.Outlined.LocationOn,
            accent = statusColors.warning,
            container = statusColors.warningContainer,
            title = stringResource(R.string.alerts_geofence_title),
            // Sin nombre de zona guardado se usa el texto genérico, sin inventar uno.
            description = if (detail != null) {
                stringResource(R.string.alerts_geofence_description_with_zone, detail)
            } else {
                stringResource(R.string.alerts_geofence_description)
            }
        )
        AlertType.SECURITY -> AlertCardStyle(
            icon = Icons.Outlined.Lock,
            accent = statusColors.critical,
            container = statusColors.criticalContainer,
            title = stringResource(R.string.alerts_security_title),
            description = stringResource(R.string.alerts_security_description)
        )
    }
}

/** Sin valor medido no se dice hacia qué lado se salió ni se inventa una cifra. */
@Composable
private fun temperatureDescription(celsius: Float?): String {
    if (celsius == null || celsius.isNaN()) {
        return stringResource(R.string.alerts_temperature_unknown)
    }
    val temperatureText = stringResource(R.string.temperature_value, celsius)
    return if (celsius > TemperatureStatus.MAX_SAFE_CELSIUS) {
        stringResource(R.string.alerts_temperature_high, temperatureText)
    } else {
        stringResource(R.string.alerts_temperature_low, temperatureText)
    }
}

@Preview(showBackground = true, name = "Tarjetas de alerta")
@Composable
private fun AlertCardPreview() {
    val alerts = listOf(
        AlertItem(id = 1, type = AlertType.TEMPERATURE, value = 8.4f, elapsed = RelativeTime.Minutes(5)),
        AlertItem(
            id = 2,
            type = AlertType.GEOFENCE,
            value = null,
            elapsed = RelativeTime.Minutes(23),
            detail = "Hospital Clínico"
        ),
        AlertItem(id = 3, type = AlertType.SECURITY, value = null, elapsed = RelativeTime.Hours(1)),
        AlertItem(id = 4, type = AlertType.BATTERY, value = 15f, elapsed = RelativeTime.Hours(3)),
        AlertItem(id = 5, type = AlertType.CONNECTION, value = null, elapsed = RelativeTime.Hours(12))
    )
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                alerts.forEach { AlertCard(alert = it) }
            }
        }
    }
}
