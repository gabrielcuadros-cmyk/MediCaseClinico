package com.example.medicaseclinico.ui.screens.dashboard.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.ui.screens.dashboard.CaseLockStatus
import com.example.medicaseclinico.ui.theme.MediCaseTheme

/**
 * Tarjeta "Cierre" del Dashboard: si el cierre electrónico del estuche está
 * cerrado o abierto, según lo informa el propio estuche.
 *
 * Sin ese dato (sin enlace, o un estuche sin servicio de control) muestra
 * "--" y explica por qué: nunca se da por cerrado un estuche que no lo ha dicho.
 */
@Composable
fun LockCard(
    status: CaseLockStatus,
    modifier: Modifier = Modifier
) {
    val accent = when (status) {
        CaseLockStatus.LOCKED -> MaterialTheme.colorScheme.primary
        // Abierto no es un fallo, pero conviene que llame la atención.
        CaseLockStatus.OPEN -> MediCaseTheme.statusColors.warning
        CaseLockStatus.UNKNOWN, CaseLockStatus.NOT_SUPPORTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        // Un solo elemento para los lectores de pantalla: "Cierre, Cerrado, …".
        modifier = modifier.semantics(mergeDescendants = true) { },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.lock_card_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (status == CaseLockStatus.OPEN) {
                        Icons.Outlined.LockOpen
                    } else {
                        Icons.Outlined.Lock
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(status.valueRes()),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(status.detailRes()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@StringRes
private fun CaseLockStatus.valueRes(): Int = when (this) {
    CaseLockStatus.LOCKED -> R.string.lock_value_locked
    CaseLockStatus.OPEN -> R.string.lock_value_open
    CaseLockStatus.UNKNOWN, CaseLockStatus.NOT_SUPPORTED -> R.string.value_unknown
}

@StringRes
private fun CaseLockStatus.detailRes(): Int = when (this) {
    CaseLockStatus.LOCKED -> R.string.lock_detail_locked
    CaseLockStatus.OPEN -> R.string.lock_detail_open
    CaseLockStatus.UNKNOWN -> R.string.lock_detail_unknown
    CaseLockStatus.NOT_SUPPORTED -> R.string.security_case_not_supported
}

@Preview(showBackground = true, name = "Cierre - cerrado")
@Composable
private fun LockCardLockedPreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LockCard(status = CaseLockStatus.LOCKED, modifier = Modifier.padding(16.dp))
        }
    }
}

@Preview(showBackground = true, name = "Cierre - sin dato")
@Composable
private fun LockCardUnknownPreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LockCard(status = CaseLockStatus.UNKNOWN, modifier = Modifier.padding(16.dp))
        }
    }
}
