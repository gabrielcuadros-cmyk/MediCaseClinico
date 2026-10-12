package com.example.medicaseclinico.ui.screens.alerts.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.AlertFilter
import com.example.medicaseclinico.ui.theme.MediCaseTheme

/**
 * Fila de chips "Todas · Temperatura · Ubicación · Seguridad".
 *
 * Se desplaza en horizontal porque en pantallas estrechas no caben los cuatro,
 * igual que en el diseño. Sin estado propio: el filtro vive en el ViewModel.
 * Cada chip se anuncia como seleccionado o no, para que la selección no
 * dependa solo del color.
 *
 * @param contentPadding margen dentro del área desplazable, para que los
 * chips lleguen hasta el borde de la pantalla al deslizar.
 */
@Composable
fun AlertFilterChips(
    selectedFilter: AlertFilter,
    onFilterSelected: (AlertFilter) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (filter in AlertFilter.entries) {
            val isSelected = filter == selectedFilter
            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onFilterSelected(filter) }
                    ),
                shape = CircleShape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                border = if (isSelected) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                }
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(filter.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@StringRes
private fun AlertFilter.labelRes(): Int = when (this) {
    AlertFilter.ALL -> R.string.alerts_filter_all
    AlertFilter.TEMPERATURE -> R.string.alerts_filter_temperature
    AlertFilter.LOCATION -> R.string.alerts_filter_location
    AlertFilter.SECURITY -> R.string.alerts_filter_security
}

@Preview(showBackground = true, name = "Chips de filtro de alertas")
@Composable
private fun AlertFilterChipsPreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AlertFilterChips(
                selectedFilter = AlertFilter.ALL,
                onFilterSelected = {},
                contentPadding = PaddingValues(16.dp)
            )
        }
    }
}
