package com.example.medicaseclinico.ui.screens.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.ui.theme.MediCaseTheme

/**
 * Aviso del Inicio mientras el Modo Pérdida está activo.
 *
 * Va lo primero de la pantalla: un estuche perdido importa más que cualquier
 * otra cosa que el Inicio pueda mostrar. Todo el aviso se puede tocar y lleva
 * a las herramientas para recuperarlo. Lo dice con ícono y texto, no solo con
 * el color rojo.
 */
@Composable
fun LossModeBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val critical = MediCaseTheme.statusColors.critical
    val openLabel = stringResource(R.string.dashboard_loss_mode_open)

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                role = Role.Button
                onClick(label = openLabel, action = null)
            },
        shape = RoundedCornerShape(14.dp),
        color = MediCaseTheme.statusColors.criticalContainer,
        border = BorderStroke(1.dp, critical)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = critical,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.dashboard_loss_mode_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = critical
                )
                Text(
                    text = stringResource(R.string.dashboard_loss_mode_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = critical
            )
        }
    }
}

@Preview(showBackground = true, name = "Aviso de Modo Pérdida")
@Composable
private fun LossModeBannerPreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LossModeBanner(onClick = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
