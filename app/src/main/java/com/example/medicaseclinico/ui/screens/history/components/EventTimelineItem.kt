package com.example.medicaseclinico.ui.screens.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.common.HistoryEventTextFormatter
import com.example.medicaseclinico.domain.model.HistoryEvent
import com.example.medicaseclinico.domain.model.TemperatureExcursion
import com.example.medicaseclinico.domain.time.DateTimeLabels
import com.example.medicaseclinico.domain.time.EventTimeLabel
import com.example.medicaseclinico.ui.theme.MediCaseTheme

private val IconCircleSize = 32.dp

/**
 * Fila de la "Línea de eventos": ícono en círculo, título, descripción y hora.
 *
 * Cada tipo de evento tiene ícono y texto propios, de modo que una alerta se
 * distingue de una normalización sin depender del color.
 *
 * @param referenceTime "ahora" de la pantalla (epoch ms); decide si la hora
 * se muestra sola, con "Ayer" o con fecha.
 * @param isLast la última fila no dibuja la línea que la une a la siguiente.
 */
@Composable
fun EventTimelineItem(
    event: HistoryEvent,
    referenceTime: Long,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val style = event.timelineStyle()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {}
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = style.container,
                modifier = Modifier.size(IconCircleSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = null,
                        tint = style.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outline)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp, bottom = 18.dp)
        ) {
            Text(
                text = style.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = style.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = eventTimeLabel(timestamp = event.timestamp, referenceTime = referenceTime),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private data class TimelineStyle(
    val icon: ImageVector,
    val accent: Color,
    val container: Color,
    val title: String,
    val description: String
)

/** Ícono y colores son cosa de la pantalla; los textos salen del formateador común. */
@Composable
private fun HistoryEvent.timelineStyle(): TimelineStyle {
    val statusColors = MediCaseTheme.statusColors
    val resources = LocalContext.current.resources
    // Se relee la configuración para rehacer los textos si cambia el idioma.
    val configuration = LocalConfiguration.current
    val texts = remember(resources, configuration) { HistoryEventTextFormatter(resources) }
    val title = texts.title(this)
    val description = texts.description(this)

    return when (this) {
        is HistoryEvent.TemperatureAlert -> TimelineStyle(
            icon = Icons.Outlined.WarningAmber,
            accent = statusColors.critical,
            container = statusColors.criticalContainer,
            title = title,
            description = description
        )
        is HistoryEvent.TemperatureNormalized -> TimelineStyle(
            icon = Icons.Outlined.DeviceThermostat,
            accent = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.primaryContainer,
            title = title,
            description = description
        )
        is HistoryEvent.BatteryLow -> TimelineStyle(
            icon = Icons.Outlined.BatteryAlert,
            accent = statusColors.warning,
            container = statusColors.warningContainer,
            title = title,
            description = description
        )
    }
}

/** Hora del evento como en el diseño: "09:30", "Ayer 22:11" o "5 oct, 22:11". */
@Composable
private fun eventTimeLabel(timestamp: Long, referenceTime: Long): String {
    val dateTimeLabels = remember { DateTimeLabels.system() }

    return when (val label = dateTimeLabels.eventTime(timestamp, referenceTime)) {
        is EventTimeLabel.Today -> label.time
        is EventTimeLabel.Yesterday -> stringResource(R.string.history_event_yesterday, label.time)
        is EventTimeLabel.Earlier ->
            stringResource(R.string.history_event_date_time, label.date, label.time)
    }
}

@Preview(showBackground = true, name = "Línea de eventos")
@Composable
private fun EventTimelinePreview() {
    val now = 1_760_000_000_000L
    val hour = 3_600_000L
    val events = listOf(
        HistoryEvent.BatteryLow(timestamp = now - hour, levelPercent = 18),
        HistoryEvent.TemperatureNormalized(
            timestamp = now - 3 * hour,
            celsius = 4.2f,
            recoveredFrom = TemperatureExcursion.HIGH
        ),
        HistoryEvent.TemperatureAlert(
            timestamp = now - 26 * hour,
            direction = TemperatureExcursion.HIGH,
            peakCelsius = 8.1f,
            endTimestamp = now - 25 * hour
        )
    )
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                events.forEachIndexed { index, event ->
                    EventTimelineItem(
                        event = event,
                        referenceTime = now,
                        isLast = index == events.lastIndex
                    )
                }
            }
        }
    }
}
