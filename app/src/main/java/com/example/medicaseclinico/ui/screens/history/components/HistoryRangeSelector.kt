package com.example.medicaseclinico.ui.screens.history.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.HistoryRange
import com.example.medicaseclinico.ui.theme.MediCaseTheme

private val SegmentShape = RoundedCornerShape(9.dp)

/**
 * Selector segmentado "Últimas 24h · 7 Días · 30 Días" del Historial Térmico.
 *
 * Sin estado propio: el rango elegido vive en el ViewModel. Cada segmento se
 * anuncia como pestaña seleccionada o no, para que la selección no dependa
 * solo del color de fondo.
 */
@Composable
fun HistoryRangeSelector(
    selectedRange: HistoryRange,
    onRangeSelected: (HistoryRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(4.dp)
        ) {
            for (range in HistoryRange.entries) {
                val isSelected = range == selectedRange
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(SegmentShape)
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onRangeSelected(range) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.matchParentSize(),
                        shape = SegmentShape,
                        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                    ) {}
                    Text(
                        text = stringResource(range.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@StringRes
private fun HistoryRange.labelRes(): Int = when (this) {
    HistoryRange.LAST_24_HOURS -> R.string.history_range_24h
    HistoryRange.LAST_7_DAYS -> R.string.history_range_7d
    HistoryRange.LAST_30_DAYS -> R.string.history_range_30d
}

@Preview(showBackground = true, name = "Selector de rango")
@Composable
private fun HistoryRangeSelectorPreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HistoryRangeSelector(
                selectedRange = HistoryRange.LAST_24_HOURS,
                onRangeSelected = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
