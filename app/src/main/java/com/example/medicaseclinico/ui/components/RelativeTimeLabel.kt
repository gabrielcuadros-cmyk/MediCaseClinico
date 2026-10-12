package com.example.medicaseclinico.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.RelativeTime

/**
 * Texto de un tiempo transcurrido: "Ahora", "Hace 5 min", "Hace 1 hora",
 * "Hace 3 días".
 *
 * Sale de `strings.xml` y no de `DateUtils` porque este último usa el idioma
 * del sistema: en un teléfono en inglés mezclaba "5 minutes ago" con el resto
 * de la app en español. Es un único lugar para que todas las pantallas lo
 * digan igual.
 */
@Composable
fun relativeTimeLabel(time: RelativeTime): String = when (time) {
    RelativeTime.JustNow -> stringResource(R.string.relative_time_now)
    is RelativeTime.Minutes ->
        pluralStringResource(R.plurals.relative_time_minutes, time.count, time.count)
    is RelativeTime.Hours ->
        pluralStringResource(R.plurals.relative_time_hours, time.count, time.count)
    is RelativeTime.Days ->
        pluralStringResource(R.plurals.relative_time_days, time.count, time.count)
}
