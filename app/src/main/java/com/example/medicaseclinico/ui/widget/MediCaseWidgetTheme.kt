package com.example.medicaseclinico.ui.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProvider
import androidx.glance.material3.ColorProviders
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.ui.theme.AlertRed
import com.example.medicaseclinico.ui.theme.AlertRedContainer
import com.example.medicaseclinico.ui.theme.AlertRedDarkContainer
import com.example.medicaseclinico.ui.theme.AlertRedLight
import com.example.medicaseclinico.ui.theme.MediCaseDarkColors
import com.example.medicaseclinico.ui.theme.MediCaseLightColors
import com.example.medicaseclinico.ui.theme.SafeGreen
import com.example.medicaseclinico.ui.theme.SafeGreenContainer
import com.example.medicaseclinico.ui.theme.SafeGreenDarkContainer
import com.example.medicaseclinico.ui.theme.SafeGreenLight
import com.example.medicaseclinico.ui.theme.WarningAmber
import com.example.medicaseclinico.ui.theme.WarningAmberContainer
import com.example.medicaseclinico.ui.theme.WarningAmberDarkContainer
import com.example.medicaseclinico.ui.theme.WarningAmberLight
import androidx.glance.unit.ColorProvider as GlanceColor

/** Colores de la app para Glance, con su variante clara y oscura. */
private val MediCaseWidgetColors = ColorProviders(
    light = MediCaseLightColors,
    dark = MediCaseDarkColors
)

/**
 * Tema del widget.
 *
 * Las pautas de widgets de Android piden color dinámico: en Android 12+ el
 * widget toma los colores del fondo de pantalla, como el resto del launcher.
 * En versiones anteriores usa la paleta de MediCase. Claro u oscuro siguen
 * al sistema, porque el launcher no conoce la apariencia elegida en la app.
 */
@Composable
fun MediCaseWidgetTheme(content: @Composable () -> Unit) {
    GlanceTheme(
        colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceTheme.colors
        } else {
            MediCaseWidgetColors
        },
        content = content
    )
}

/**
 * Colores clínicos del estado de la temperatura. No siguen el color dinámico:
 * verde, ámbar y rojo deben significar lo mismo con cualquier fondo de
 * pantalla (y el texto de la insignia repite el estado, nunca solo el color).
 */
internal object WidgetStatusColors {
    fun accent(status: TemperatureStatus): GlanceColor = when (status) {
        TemperatureStatus.SAFE -> ColorProvider(day = SafeGreen, night = SafeGreenLight)
        TemperatureStatus.WARNING -> ColorProvider(day = WarningAmber, night = WarningAmberLight)
        TemperatureStatus.CRITICAL -> ColorProvider(day = AlertRed, night = AlertRedLight)
    }

    fun container(status: TemperatureStatus): GlanceColor = when (status) {
        TemperatureStatus.SAFE ->
            ColorProvider(day = SafeGreenContainer, night = SafeGreenDarkContainer)
        TemperatureStatus.WARNING ->
            ColorProvider(day = WarningAmberContainer, night = WarningAmberDarkContainer)
        TemperatureStatus.CRITICAL ->
            ColorProvider(day = AlertRedContainer, night = AlertRedDarkContainer)
    }
}
