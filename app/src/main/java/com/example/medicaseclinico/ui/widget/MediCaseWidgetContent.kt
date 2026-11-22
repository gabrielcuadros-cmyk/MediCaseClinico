package com.example.medicaseclinico.ui.widget

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.medicaseclinico.MainActivity
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.CaseWidgetSummary
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.ui.navigation.LaunchDestination
import com.example.medicaseclinico.ui.navigation.TopLevelDestination

/**
 * Contenido del widget "Estado de la insulina".
 *
 * Sigue las pautas de widgets de Android (Android UI Kit): Scaffold y
 * TitleBar de Glance (fondo, esquinas y márgenes del sistema), jerarquía
 * clara con la temperatura como dato principal, botones de 48 dp y un diseño
 * por tamaño: el compacto (2×2) solo da el dato y el completo (4×2) añade los
 * accesos directos.
 *
 * @param onRetry vuelve a leer los datos tras un error.
 */
@Composable
fun MediCaseWidgetContent(state: CaseWidgetUiState, onRetry: () -> Unit) {
    val context = LocalContext.current
    val isFull = LocalSize.current.width >= MediCaseWidget.FULL.width &&
        LocalSize.current.height >= MediCaseWidget.FULL.height
    // Tocar el widget abre el historial térmico: es la pantalla de su dato principal.
    val openHistory = openAppTab(context, TopLevelDestination.TEMPERATURE)

    Scaffold(
        modifier = GlanceModifier.clickable(openHistory).semantics {
            contentDescription = context.getString(R.string.widget_open_app)
        },
        titleBar = if (isFull) {
            {
                TitleBar(
                    startIcon = ImageProvider(R.drawable.ic_map_thermometer),
                    title = context.getString(R.string.widget_title)
                )
            }
        } else {
            null
        },
        backgroundColor = GlanceTheme.colors.widgetBackground
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(top = if (isFull) 0.dp else EDGE_PADDING, bottom = EDGE_PADDING)
        ) {
            when (state) {
                CaseWidgetUiState.Loading ->
                    MessageBlock(text = context.getString(R.string.widget_loading))

                CaseWidgetUiState.Error ->
                    ErrorBlock(onRetry = onRetry)

                is CaseWidgetUiState.Ready -> {
                    if (!isFull) CompactHeader(connection = state.summary.connection)
                    ReadingBlock(
                        summary = state.summary,
                        readingTime = state.readingTime,
                        large = isFull,
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    )
                    if (isFull) ShortcutRow(unreadAlerts = state.summary.unreadAlerts)
                }
            }
        }
    }
}

/** En el tamaño compacto no hay TitleBar: el título cambia a "Sin conexión" si no hay enlace. */
@Composable
private fun CompactHeader(connection: ConnectionStatus) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.ic_map_thermometer),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(16.dp)
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = if (connection.isLive) {
                context.getString(R.string.widget_title)
            } else {
                context.getString(connectionLabelRes(connection))
            },
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

/** Temperatura, insignia de estado y, en el tamaño completo, enlace y hora de la lectura. */
@Composable
private fun ReadingBlock(
    summary: CaseWidgetSummary,
    readingTime: String?,
    large: Boolean,
    modifier: GlanceModifier
) {
    val context = LocalContext.current
    Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        when (summary) {
            is CaseWidgetSummary.NoReadings -> {
                TemperatureText(text = context.getString(R.string.widget_temperature_empty), large = large)
                SecondaryText(text = context.getString(R.string.widget_no_readings))
            }

            is CaseWidgetSummary.Reading -> {
                val temperature = context.getString(R.string.temperature_value, summary.celsius)
                val statusLabel = context.getString(statusLabelRes(summary.status))
                Row(
                    modifier = GlanceModifier.fillMaxWidth().semantics {
                        contentDescription = context.getString(
                            R.string.temperature_card_content_description,
                            temperature,
                            statusLabel
                        )
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TemperatureText(
                        text = temperature,
                        large = large,
                        // Una lectura desactualizada se atenúa: no es el estado de ahora.
                        status = summary.status.takeUnless { summary.isOutdated }
                    )
                    if (large && !summary.isOutdated) {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        StatusBadge(status = summary.status, label = statusLabel)
                    }
                }
                if (!large && !summary.isOutdated) {
                    StatusBadge(status = summary.status, label = statusLabel)
                }
                if (large && readingTime != null) {
                    SecondaryText(
                        text = context.getString(
                            R.string.widget_connection_with_time,
                            context.getString(connectionLabelRes(summary.connection)),
                            readingTime
                        )
                    )
                } else if (summary.isOutdated && readingTime != null) {
                    SecondaryText(text = readingTime)
                }
            }
        }
    }
}

@Composable
private fun TemperatureText(text: String, large: Boolean, status: TemperatureStatus? = null) {
    Text(
        text = text,
        style = TextStyle(
            // Sin estado (lectura desactualizada) se atenúa; fuera de "seguro", color clínico.
            color = when (status) {
                null -> GlanceTheme.colors.onSurfaceVariant
                TemperatureStatus.SAFE -> GlanceTheme.colors.onSurface
                else -> WidgetStatusColors.accent(status)
            },
            fontSize = if (large) 34.sp else 28.sp,
            fontWeight = FontWeight.Bold
        ),
        maxLines = 1
    )
}

/** Insignia "SEGURO" / "PRECAUCIÓN" / "CRÍTICO", como la del Dashboard. */
@Composable
private fun StatusBadge(status: TemperatureStatus, label: String) {
    Box(
        modifier = GlanceModifier
            .background(WidgetStatusColors.container(status))
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = WidgetStatusColors.accent(status),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun SecondaryText(text: String) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        maxLines = 1
    )
}

@Composable
private fun MessageBlock(text: String) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SecondaryText(text = text)
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
        SecondaryText(text = context.getString(R.string.widget_error))
        Spacer(modifier = GlanceModifier.height(8.dp))
        FilledButton(
            text = context.getString(R.string.widget_retry),
            onClick = onRetry
        )
    }
}

/** Accesos directos a las tres funciones que más se consultan desde fuera de la app. */
@Composable
private fun ShortcutRow(unreadAlerts: Int) {
    val context = LocalContext.current
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Shortcut(
            iconRes = R.drawable.ic_widget_history,
            label = context.getString(R.string.widget_shortcut_history),
            action = openAppTab(context, TopLevelDestination.TEMPERATURE),
            modifier = GlanceModifier.defaultWeight()
        )
        Spacer(modifier = GlanceModifier.width(SHORTCUT_SPACING))
        Shortcut(
            iconRes = R.drawable.ic_widget_map,
            label = context.getString(R.string.widget_shortcut_map),
            action = openAppTab(context, TopLevelDestination.MAP),
            modifier = GlanceModifier.defaultWeight()
        )
        Spacer(modifier = GlanceModifier.width(SHORTCUT_SPACING))
        Shortcut(
            iconRes = R.drawable.ic_widget_alerts,
            label = if (unreadAlerts > 0) {
                context.getString(R.string.widget_shortcut_alerts_count, unreadAlerts)
            } else {
                context.getString(R.string.widget_shortcut_alerts)
            },
            action = openAppTab(context, TopLevelDestination.ALERTS),
            modifier = GlanceModifier.defaultWeight()
        )
    }
}

@Composable
private fun Shortcut(
    @DrawableRes iconRes: Int,
    label: String,
    action: Action,
    modifier: GlanceModifier
) {
    Column(
        modifier = modifier
            .height(SHORTCUT_HEIGHT)
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(12.dp)
            .clickable(action)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            modifier = GlanceModifier.size(20.dp)
        )
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onSecondaryContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

/**
 * Abre la app en [destination]; interna para que la compartan todos los widgets.
 * SINGLE_TOP + CLEAR_TOP: si la app ya estaba abierta, recibe el pedido en
 * onNewIntent en lugar de apilar otra copia.
 */
internal fun openAppTab(context: Context, destination: TopLevelDestination): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(LaunchDestination.EXTRA_ROUTE, destination.route)
    )

@StringRes
private fun statusLabelRes(status: TemperatureStatus): Int = when (status) {
    TemperatureStatus.SAFE -> R.string.temperature_status_safe
    TemperatureStatus.WARNING -> R.string.temperature_status_warning
    TemperatureStatus.CRITICAL -> R.string.temperature_status_critical
}

@StringRes
private fun connectionLabelRes(connection: ConnectionStatus): Int = when (connection) {
    ConnectionStatus.CONNECTED -> R.string.widget_connection_live
    ConnectionStatus.CONNECTING -> R.string.widget_connection_connecting
    ConnectionStatus.DISCONNECTED -> R.string.widget_connection_offline
}

private val EDGE_PADDING = 12.dp
private val SHORTCUT_HEIGHT = 48.dp
private val SHORTCUT_SPACING = 8.dp
