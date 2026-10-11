package com.example.medicaseclinico.ui.screens.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BatteryUnknown
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.ui.theme.MediCaseTheme

/**
 * Tarjeta "Batería Estuche" del Dashboard.
 *
 * @param batteryLevel nivel real reportado por el estuche (0–100) o null si aún no se ha recibido.
 * @param isLow calculado en DashboardUiState.Success.isBatteryLow (< 20 %).
 */
@Composable
fun BatteryCard(
    batteryLevel: Int?,
    isLow: Boolean,
    modifier: Modifier = Modifier
) {
    val statusColors = MediCaseTheme.statusColors
    val accent by animateColorAsState(
        targetValue = when {
            batteryLevel == null -> MaterialTheme.colorScheme.onSurfaceVariant
            isLow -> statusColors.warning
            else -> statusColors.safe
        },
        label = "batteryAccent"
    )
    val icon = when {
        batteryLevel == null -> Icons.AutoMirrored.Outlined.BatteryUnknown
        isLow -> Icons.Outlined.BatteryAlert
        else -> Icons.Outlined.BatteryFull
    }
    val valueText = batteryLevel?.let { stringResource(R.string.battery_value, it) }
        ?: stringResource(R.string.battery_unknown)
    val accessibilityText = stringResource(R.string.battery_content_description, valueText)

    Card(
        modifier = modifier.clearAndSetSemantics { contentDescription = accessibilityText },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.battery_card_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (batteryLevel != null) {
                LinearProgressIndicator(
                    progress = { batteryLevel / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.outline,
                    strokeCap = StrokeCap.Round
                )
            }
            if (isLow) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.battery_low_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Batería - normal")
@Composable
private fun BatteryCardPreview() {
    Surface(color = MaterialTheme.colorScheme.background) {
        BatteryCard(batteryLevel = 78, isLow = false, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, name = "Batería - sin dato")
@Composable
private fun BatteryCardUnknownPreview() {
    Surface(color = MaterialTheme.colorScheme.background) {
        BatteryCard(batteryLevel = null, isLow = false, modifier = Modifier.padding(16.dp))
    }
}