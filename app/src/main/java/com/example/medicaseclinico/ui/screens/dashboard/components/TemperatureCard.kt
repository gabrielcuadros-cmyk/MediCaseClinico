package com.example.medicaseclinico.ui.screens.dashboard.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.medicaseclinico.ui.theme.MediCaseStatusColors
import com.example.medicaseclinico.ui.theme.MediCaseTheme

/**
 * Tarjeta central del Dashboard con la temperatura de la insulina.
 *
 * Sin lógica de negocio: la clasificación (SAFE / WARNING / CRITICAL) llega
 * ya calculada desde el DashboardViewModel. Aquí solo se elige el color.
 *
 * @param temperatureCelsius última lectura real guardada en Room.
 * @param status clasificación clínica calculada con TemperatureStatus.fromCelsius().
 * @param isLiveData false si el estuche está desconectado: se avisa que el valor es la última lectura guardada y no una medición en vivo.
 */
@Composable
fun TemperatureCard(
    temperatureCelsius: Float,
    status: TemperatureStatus,
    isLiveData: Boolean,
    modifier: Modifier = Modifier
) {
    val style = status.cardStyle(MediCaseTheme.statusColors)
    // Transiciones suaves al cambiar de estado (p. ej. SAFE → CRITICAL).
    val accentColor by animateColorAsState(style.accent, label = "tempAccent")
    val borderColor by animateColorAsState(
        targetValue = if (status == TemperatureStatus.SAFE) {
            MaterialTheme.colorScheme.outline
        } else {
            style.accent
        },
        label = "tempBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (status == TemperatureStatus.CRITICAL) 2.dp else 1.dp,
        label = "tempBorderWidth"
    )

    val temperatureText = stringResource(R.string.temperature_value, temperatureCelsius)
    val statusText = stringResource(style.labelRes)
    val accessibilityText = stringResource(
        R.string.temperature_card_content_description,
        temperatureText,
        statusText
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            // Un único anuncio claro para TalkBack en lugar de leer pieza por pieza.
            .clearAndSetSemantics { contentDescription = accessibilityText },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // --- Fila superior: ícono + título + insignia de estado ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.DeviceThermostat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.temperature_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(
                    text = statusText,
                    contentColor = accentColor,
                    containerColor = style.container
                )
            }

            Spacer(modifier = Modifier.padding(top = 16.dp))

            // --- Fila inferior: valor gigante + rango óptimo ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = temperatureText,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    lineHeight = 52.sp
                )
                Text(
                    text = stringResource(
                        R.string.temperature_optimal_range,
                        TemperatureStatus.MIN_SAFE_CELSIUS,
                        TemperatureStatus.MAX_SAFE_CELSIUS
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // --- Aviso cuando el valor no es en vivo ---
            if (!isLiveData) {
                Spacer(modifier = Modifier.padding(top = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.temperature_not_live),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Insignia en mayúsculas: "SEGURO", "PRECAUCIÓN" o "CRÍTICO". */
@Composable
private fun StatusBadge(
    text: String,
    contentColor: Color,
    containerColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ------------------------------------------------------------------
//  Estilo por estado (capa UI: el enum de dominio no conoce colores)
// ------------------------------------------------------------------
private data class TemperatureCardStyle(
    val accent: Color,
    val container: Color,
    @get:StringRes val labelRes: Int
)

private fun TemperatureStatus.cardStyle(colors: MediCaseStatusColors): TemperatureCardStyle =
    when (this) {
        TemperatureStatus.SAFE -> TemperatureCardStyle(
            accent = colors.safe,
            container = colors.safeContainer,
            labelRes = R.string.temperature_status_safe
        )
        TemperatureStatus.WARNING -> TemperatureCardStyle(
            accent = colors.warning,
            container = colors.warningContainer,
            labelRes = R.string.temperature_status_warning
        )
        TemperatureStatus.CRITICAL -> TemperatureCardStyle(
            accent = colors.critical,
            container = colors.criticalContainer,
            labelRes = R.string.temperature_status_critical
        )
    }

// ------------------------------------------------------------------
//  Vistas previas
// ------------------------------------------------------------------
@Preview(showBackground = true, name = "Tarjeta - SAFE en vivo")
@Composable
private fun TemperatureCardSafePreview() {
    Surface(color = MaterialTheme.colorScheme.background) {
        TemperatureCard(
            temperatureCelsius = 4.2f,
            status = TemperatureStatus.SAFE,
            isLiveData = true,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, name = "Tarjeta - CRITICAL sin conexión")
@Composable
private fun TemperatureCardCriticalPreview() {
    Surface(color = MaterialTheme.colorScheme.background) {
        TemperatureCard(
            temperatureCelsius = 8.4f,
            status = TemperatureStatus.CRITICAL,
            isLiveData = false,
            modifier = Modifier.padding(16.dp)
        )
    }
}