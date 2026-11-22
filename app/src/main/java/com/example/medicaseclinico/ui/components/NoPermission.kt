package com.example.medicaseclinico.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.ui.theme.MediCaseTheme

/**
 * Aviso dentro de una pantalla en lugar de un dato que la cuenta invitada no
 * tiene permiso de ver. Dice el motivo en texto: un hueco vacío parecería un
 * fallo de la app.
 */
@Composable
fun NoPermissionNote(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Pantalla completa para una sección que la cuenta no puede abrir (llegó por
 * un atajo o un widget). Explica el motivo y quién puede cambiarlo.
 */
@Composable
fun NoPermissionScreen(modifier: Modifier = Modifier) {
    MediCaseMessageState(
        icon = Icons.Outlined.Lock,
        title = stringResource(R.string.access_no_permission_title),
        message = stringResource(R.string.access_no_permission_message),
        accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        accentContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    )
}

@Preview(showBackground = true, name = "Sin permiso")
@Composable
private fun NoPermissionNotePreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            NoPermissionNote(
                text = stringResource(R.string.access_no_temperature_permission),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
