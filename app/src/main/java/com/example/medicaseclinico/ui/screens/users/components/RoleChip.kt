package com.example.medicaseclinico.ui.screens.users.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.domain.model.UserRole
import com.example.medicaseclinico.ui.components.labelRes
import com.example.medicaseclinico.ui.theme.MediCaseTheme

/**
 * Insignia del rol. El texto dice el rol; el color solo lo refuerza (azul para
 * quien administra, verde para quien cuida).
 *
 * @param text lo que se lee en la insignia; por defecto, el nombre del rol.
 */
@Composable
fun RoleChip(
    role: UserRole,
    modifier: Modifier = Modifier,
    text: String = stringResource(role.labelRes())
) {
    val (container, content) = when (role) {
        UserRole.PRINCIPAL, UserRole.SECONDARY_ADMIN ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        UserRole.CAREGIVER ->
            MediCaseTheme.statusColors.safeContainer to MediCaseTheme.statusColors.safe
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(6.dp), color = container) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
