package com.example.medicaseclinico.ui.screens.dashboard.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.CaseTransport
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.ui.theme.MediCaseTheme

/**
 * Cabecera del Dashboard: saludo, título y estado de conexión del estuche.
 *
 * Componente sin estado: recibe el ConnectionStatus ya calculado por el
 * DashboardViewModel y solo decide cómo pintarlo.
 *
 * @param connectionStatus estado del enlace con el estuche.
 * @param transport por dónde llegan los datos: decide el ícono de la insignia, para
 * no mostrar un Bluetooth cuando el estuche habla por MQTT.
 * @param userName nombre del usuario autenticado. Si es null se omite el saludo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    connectionStatus: ConnectionStatus,
    transport: CaseTransport,
    modifier: Modifier = Modifier,
    userName: String? = null
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                if (!userName.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.dashboard_greeting, userName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.dashboard_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        actions = {
            BleConnectionBadge(
                status = connectionStatus,
                transport = transport,
                modifier = Modifier.padding(end = 16.dp)
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
        )
    )
}

/**
 * Insignia de conexión: ícono + texto sobre fondo tenue.
 *  - CONNECTED    → verde  · "Conectado"
 *  - CONNECTING   → ámbar  · "Conectando…" (ícono con pulso)
 *  - DISCONNECTED → rojo   · "Desconectado"
 *
 * El ícono es Bluetooth o nube según el [transport]: por MQTT el teléfono no usa
 * el Bluetooth y el ícono no debe sugerirlo.
 */
@Composable
fun BleConnectionBadge(
    status: ConnectionStatus,
    transport: CaseTransport,
    modifier: Modifier = Modifier
) {
    val style = status.badgeStyle(transport)
    val label = stringResource(style.labelRes)
    val description = stringResource(R.string.ble_status_content_description, label)

    val containerColor by animateColorAsState(style.containerColor, label = "bleContainer")
    val contentColor by animateColorAsState(style.contentColor, label = "bleContent")

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .then(
                        if (status == ConnectionStatus.CONNECTING) Modifier.alpha(pulseAlpha())
                        else Modifier
                    )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Parpadeo suave solo mientras se conecta. */
@Composable
private fun pulseAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "blePulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blePulseAlpha"
    )
    return alpha
}

// ------------------------------------------------------------------
//  Estilo visual por estado
// ------------------------------------------------------------------
private data class BleBadgeStyle(
    val icon: ImageVector,
    @get:StringRes val labelRes: Int,
    val contentColor: Color,
    val containerColor: Color
)

@Composable
private fun ConnectionStatus.badgeStyle(transport: CaseTransport): BleBadgeStyle {
    val status = MediCaseTheme.statusColors
    val mqtt = transport == CaseTransport.MQTT
    return when (this) {
        ConnectionStatus.CONNECTED -> BleBadgeStyle(
            icon = if (mqtt) Icons.Filled.CloudDone else Icons.Filled.BluetoothConnected,
            labelRes = R.string.ble_status_connected,
            contentColor = status.safe,
            containerColor = status.safeContainer
        )
        ConnectionStatus.CONNECTING -> BleBadgeStyle(
            icon = if (mqtt) Icons.Filled.CloudSync else Icons.AutoMirrored.Filled.BluetoothSearching,
            labelRes = R.string.ble_status_connecting,
            contentColor = status.warning,
            containerColor = status.warningContainer
        )
        ConnectionStatus.DISCONNECTED -> BleBadgeStyle(
            icon = if (mqtt) Icons.Filled.CloudOff else Icons.Filled.BluetoothDisabled,
            labelRes = R.string.ble_status_disconnected,
            contentColor = status.critical,
            containerColor = status.criticalContainer
        )
    }
}

// ------------------------------------------------------------------
//  Vistas previas
// ------------------------------------------------------------------
@Preview(showBackground = true, name = "TopBar - Conectado")
@Composable
private fun DashboardTopBarConnectedPreview() {
    MediCaseTheme {
        DashboardTopBar(connectionStatus = ConnectionStatus.CONNECTED, transport = CaseTransport.BLUETOOTH)
    }
}

@Preview(showBackground = true, name = "TopBar - Conectado por MQTT")
@Composable
private fun DashboardTopBarConnectedMqttPreview() {
    MediCaseTheme {
        DashboardTopBar(connectionStatus = ConnectionStatus.CONNECTED, transport = CaseTransport.MQTT)
    }
}

@Preview(showBackground = true, name = "TopBar - Conectando")
@Composable
private fun DashboardTopBarConnectingPreview() {
    MediCaseTheme {
        DashboardTopBar(connectionStatus = ConnectionStatus.CONNECTING, transport = CaseTransport.BLUETOOTH)
    }
}

@Preview(showBackground = true, name = "TopBar - Desconectado")
@Composable
private fun DashboardTopBarDisconnectedPreview() {
    MediCaseTheme {
        DashboardTopBar(connectionStatus = ConnectionStatus.DISCONNECTED, transport = CaseTransport.BLUETOOTH)
    }
}