package com.example.medicaseclinico.ui.screens.map.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.RelativeTime
import com.example.medicaseclinico.ui.components.relativeTimeLabel
import com.example.medicaseclinico.ui.screens.map.MapGeofence
import com.example.medicaseclinico.ui.theme.MediCaseTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(16.dp)

/**
 * Tarjeta bajo el mapa: coordenadas del estuche, hace cuánto se recibieron y
 * el resumen de la zona de seguridad más cercana.
 *
 * Dice con texto lo mismo que el mapa dibuja, para quien no puede ver el mapa.
 *
 * @param nearestGeofence zona activa más cercana, o null si no hay ninguna.
 */
@Composable
fun CaseLocationCard(
    latitude: Double,
    longitude: Double,
    elapsed: RelativeTime,
    nearestGeofence: MapGeofence?,
    onAdjustGeofence: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                LabeledValue(
                    label = stringResource(R.string.map_coordinates_label),
                    value = coordinatesLabel(latitude, longitude),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                LabeledValue(
                    label = stringResource(R.string.map_last_update_label),
                    value = relativeTimeLabel(elapsed),
                    horizontalAlignment = Alignment.End
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledValue(
                    label = stringResource(R.string.map_geofence_label),
                    value = geofenceLabel(nearestGeofence),
                    valueColor = geofenceColor(nearestGeofence),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                AdjustButton(onClick = onAdjustGeofence)
            }
        }
    }
}

@Composable
private fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = horizontalAlignment
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = if (horizontalAlignment == Alignment.End) TextAlign.End else TextAlign.Start
        )
    }
}

/** Botón tenue "Ajustar" del diseño: lleva a configurar la zona. */
@Composable
private fun AdjustButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.semantics { role = Role.Button },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = stringResource(R.string.map_geofence_adjust),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/** "16.3989° S, 71.5369° W": hemisferio con letra en lugar de signo, como en el diseño. */
@Composable
fun coordinatesLabel(latitude: Double, longitude: Double): String {
    val latitudeText = stringResource(
        if (latitude >= 0) R.string.map_latitude_north else R.string.map_latitude_south,
        abs(latitude)
    )
    val longitudeText = stringResource(
        if (longitude >= 0) R.string.map_longitude_east else R.string.map_longitude_west,
        abs(longitude)
    )
    return stringResource(R.string.map_coordinates_value, latitudeText, longitudeText)
}

@Composable
private fun geofenceLabel(geofence: MapGeofence?): String = when {
    geofence == null -> stringResource(R.string.map_geofence_none)
    geofence.isInside -> stringResource(R.string.map_geofence_inside, geofence.radiusMeters.roundToInt())
    else -> stringResource(R.string.map_geofence_outside, geofence.radiusMeters.roundToInt())
}

/** El color acompaña al texto, que ya dice por sí solo si está dentro o fuera. */
@Composable
private fun geofenceColor(geofence: MapGeofence?): Color = when {
    geofence == null -> MaterialTheme.colorScheme.onSurfaceVariant
    geofence.isInside -> MediCaseTheme.statusColors.safe
    else -> MediCaseTheme.statusColors.critical
}

@Preview(showBackground = true, name = "Tarjeta de ubicación")
@Composable
private fun CaseLocationCardPreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CaseLocationCard(
                    latitude = 40.4167,
                    longitude = -3.7037,
                    elapsed = RelativeTime.Minutes(2),
                    nearestGeofence = MapGeofence(
                        id = 1,
                        name = "Clínica Médica Norte",
                        latitude = 40.4167,
                        longitude = -3.7037,
                        radiusMeters = 500.0,
                        isInside = true
                    ),
                    onAdjustGeofence = {}
                )
                CaseLocationCard(
                    latitude = -16.3989,
                    longitude = -71.5369,
                    elapsed = RelativeTime.Hours(3),
                    nearestGeofence = null,
                    onAdjustGeofence = {}
                )
            }
        }
    }
}
