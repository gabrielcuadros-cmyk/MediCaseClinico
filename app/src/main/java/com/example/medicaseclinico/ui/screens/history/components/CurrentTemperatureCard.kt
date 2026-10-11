package com.example.medicaseclinico.ui.screens.history.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.ui.screens.history.CurrentTemperature
import com.example.medicaseclinico.ui.theme.MediCaseStatusColors
import com.example.medicaseclinico.ui.theme.MediCaseTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val READING_TIME_PATTERN = "d MMM, HH:mm"

/**
 * Tarjeta "Temperatura actual" del Historial Térmico con su chip de estado.
 *
 * Muestra también la hora de la lectura: si el estuche lleva tiempo sin
 * conexión, el usuario ve que el valor no es de ahora en lugar de confiar en
 * un dato viejo.
 *
 * @param current última lectura real ya clasificada, o null si el estuche
 * nunca ha reportado: se muestra "--" y ningún chip.
 */
@Composable
fun CurrentTemperatureCard(
    current: CurrentTemperature?,
    modifier: Modifier = Modifier
) {
    val style = current?.status?.chipStyle(MediCaseTheme.statusColors)
    val valueText = if (current != null) {
        stringResource(R.string.temperature_value, current.celsius)
    } else {
        stringResource(R.string.history_value_unknown)
    }
    val statusText = style?.let { stringResource(it.labelRes) }
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern(READING_TIME_PATTERN, Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }
    val readingTimeText = current?.let {
        stringResource(
            R.string.history_current_reading_time,
            timeFormatter.format(Instant.ofEpochMilli(it.timestamp))
        )
    }
    val accessibilityText = if (statusText != null && readingTimeText != null) {
        stringResource(
            R.string.history_current_description,
            valueText,
            statusText,
            readingTimeText
        )
    } else {
        stringResource(R.string.history_current_unknown_description)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = accessibilityText },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.history_current_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = valueText,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (readingTimeText != null) {
                    Text(
                        text = readingTimeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (style != null && statusText != null) {
                StatusChip(text = statusText, accent = style.accent, container = style.container)
            }
        }
    }
}

/** Chip en forma de píldora: punto de color + texto, como "● RANGO SEGURO". */
@Composable
private fun StatusChip(text: String, accent: Color, container: Color) {
    Surface(shape = CircleShape, color = container, contentColor = accent) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color = accent, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class StatusChipStyle(
    val accent: Color,
    val container: Color,
    @get:StringRes val labelRes: Int
)

private fun TemperatureStatus.chipStyle(colors: MediCaseStatusColors): StatusChipStyle =
    when (this) {
        TemperatureStatus.SAFE -> StatusChipStyle(
            accent = colors.safe,
            container = colors.safeContainer,
            labelRes = R.string.history_status_safe
        )
        TemperatureStatus.WARNING -> StatusChipStyle(
            accent = colors.warning,
            container = colors.warningContainer,
            labelRes = R.string.history_status_warning
        )
        TemperatureStatus.CRITICAL -> StatusChipStyle(
            accent = colors.critical,
            container = colors.criticalContainer,
            labelRes = R.string.history_status_critical
        )
    }

@Preview(showBackground = true, name = "Temperatura actual - Rango seguro")
@Composable
private fun CurrentTemperatureCardSafePreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CurrentTemperatureCard(
                current = CurrentTemperature(
                    celsius = 4.2f,
                    status = TemperatureStatus.SAFE,
                    timestamp = 1_760_000_000_000L
                ),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "Temperatura actual - Fuera de rango")
@Composable
private fun CurrentTemperatureCardCriticalPreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CurrentTemperatureCard(
                current = CurrentTemperature(
                    celsius = 9.1f,
                    status = TemperatureStatus.CRITICAL,
                    timestamp = 1_760_000_000_000L
                ),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "Temperatura actual - Sin dato")
@Composable
private fun CurrentTemperatureCardUnknownPreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CurrentTemperatureCard(current = null, modifier = Modifier.padding(16.dp))
        }
    }
}
