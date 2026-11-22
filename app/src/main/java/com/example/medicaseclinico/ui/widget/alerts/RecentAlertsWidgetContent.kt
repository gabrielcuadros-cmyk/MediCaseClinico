package com.example.medicaseclinico.ui.widget.alerts

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.AlertType
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.time.EventTimeLabel
import com.example.medicaseclinico.ui.navigation.TopLevelDestination
import com.example.medicaseclinico.ui.widget.WidgetStatusColors
import com.example.medicaseclinico.ui.widget.openAppTab

/**
 * Contenido del widget "Últimas alertas".
 *
 * Patrón de lista de las pautas de widgets de Android: TitleBar con el
 * contador de alertas sin ver y una lista desplazable de filas de 48 dp. Cada
 * fila repite el tipo en texto (el color de la barra lateral solo refuerza) y
 * al tocarla abre "Alertas de Seguridad" en la app.
 *
 * @param onRetry vuelve a leer las alertas tras un error.
 */
@Composable
fun RecentAlertsWidgetContent(state: RecentAlertsUiState, onRetry: () -> Unit) {
    val context = LocalContext.current
    val openAlerts = openAppTab(context, TopLevelDestination.ALERTS)

    Scaffold(
        modifier = GlanceModifier.clickable(openAlerts).semantics {
            contentDescription = context.getString(R.string.alerts_widget_open)
        },
        titleBar = {
            TitleBar(
                startIcon = ImageProvider(R.drawable.ic_widget_alerts),
                title = context.getString(R.string.alerts_widget_title),
                actions = {
                    val unread = (state as? RecentAlertsUiState.Ready)?.unreadCount ?: 0
                    if (unread > 0) {
                        Text(
                            text = context.getString(R.string.alerts_widget_unread, unread),
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.padding(end = 12.dp)
                        )
                    }
                }
            )
        },
        backgroundColor = GlanceTheme.colors.widgetBackground
    ) {
        Box(modifier = GlanceModifier.fillMaxSize().padding(bottom = 12.dp)) {
            when (state) {
                RecentAlertsUiState.Loading ->
                    CenteredText(text = context.getString(R.string.alerts_widget_loading))

                RecentAlertsUiState.Error -> ErrorBlock(onRetry = onRetry)

                RecentAlertsUiState.SignedOut ->
                    CenteredText(text = context.getString(R.string.alerts_widget_signed_out))

                is RecentAlertsUiState.Ready ->
                    if (state.items.isEmpty()) {
                        CenteredText(text = context.getString(R.string.alerts_widget_empty))
                    } else {
                        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            items(state.items, itemId = { it.event.id }) { item ->
                                AlertRow(item = item)
                            }
                        }
                    }
            }
        }
    }
}

/** Una alerta: barra de color, título y hora. Las no vistas van en negrita. */
@Composable
private fun AlertRow(item: RecentAlertItem) {
    val context = LocalContext.current
    val title = context.getString(titleRes(item.event.type))
    val time = timeText(context, item.time)
    val description = context.getString(
        if (item.event.isRead) {
            R.string.alerts_widget_item_description
        } else {
            R.string.alerts_widget_item_unread_description
        },
        title,
        time
    )

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(12.dp)
                .clickable(openAppTab(context, TopLevelDestination.ALERTS))
                .semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier
                    .width(4.dp)
                    .height(28.dp)
                    .cornerRadius(2.dp)
                    .background(accentFor(item.event.type))
            ) {}
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = if (item.event.isRead) FontWeight.Normal else FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = time,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
        }
        Spacer(modifier = GlanceModifier.height(ROW_SPACING))
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            maxLines = 2
        )
    }
}

@Composable
private fun ErrorBlock(onRetry: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = context.getString(R.string.alerts_widget_error),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            maxLines = 2
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        FilledButton(text = context.getString(R.string.alerts_widget_retry), onClick = onRetry)
    }
}

/**
 * Mismos colores que las tarjetas de "Alertas de Seguridad": rojo para lo que
 * pone en riesgo la insulina o el acceso, ámbar para lo que pide atención, el
 * color principal para los avisos de conexión y verde para un acceso correcto.
 */
@Composable
private fun accentFor(type: AlertType): ColorProvider = when (type) {
    AlertType.TEMPERATURE, AlertType.SECURITY -> WidgetStatusColors.accent(TemperatureStatus.CRITICAL)
    AlertType.BATTERY, AlertType.GEOFENCE -> WidgetStatusColors.accent(TemperatureStatus.WARNING)
    AlertType.CONNECTION -> GlanceTheme.colors.primary
    AlertType.ACCESS_GRANTED -> WidgetStatusColors.accent(TemperatureStatus.SAFE)
}

/** Los mismos títulos que la pantalla de Alertas, para que el usuario reconozca cada una. */
private fun titleRes(type: AlertType): Int = when (type) {
    AlertType.TEMPERATURE -> R.string.alerts_temperature_title
    AlertType.BATTERY -> R.string.alerts_battery_title
    AlertType.CONNECTION -> R.string.alerts_connection_title
    AlertType.GEOFENCE -> R.string.alerts_geofence_title
    AlertType.SECURITY -> R.string.alerts_security_title
    AlertType.ACCESS_GRANTED -> R.string.alerts_access_granted_title
}

/** "14:32" hoy, "Ayer 14:32" o "5 oct, 14:32"; hora exacta porque el widget no se redibuja cada minuto. */
private fun timeText(context: Context, time: EventTimeLabel): String = when (time) {
    is EventTimeLabel.Today -> time.time
    is EventTimeLabel.Yesterday -> context.getString(R.string.history_event_yesterday, time.time)
    is EventTimeLabel.Earlier ->
        context.getString(R.string.alerts_widget_time_earlier, time.date, time.time)
}

private val ROW_HEIGHT = 48.dp
private val ROW_SPACING = 6.dp
