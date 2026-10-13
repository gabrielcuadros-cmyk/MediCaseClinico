package com.example.medicaseclinico.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

// ============================================================
//  Esquemas de color Material 3
// ============================================================
private val MediCaseLightColors = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = PureWhite,
    primaryContainer = ElectricBlueContainer,
    onPrimaryContainer = ElectricBlueDark,
    secondary = ElectricBlueDark,
    onSecondary = PureWhite,
    background = ClinicBackground,
    onBackground = TextPrimary,
    surface = PureWhite,
    onSurface = TextPrimary,
    surfaceVariant = ClinicBackground,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = PureWhite,
    surfaceContainer = PureWhite,
    surfaceContainerHigh = SurfacePressed,
    surfaceContainerHighest = PureWhite,
    outline = SurfaceBorder,
    outlineVariant = SurfaceBorder,
    error = AlertRed,
    onError = PureWhite,
    errorContainer = AlertRedContainer,
    onErrorContainer = AlertRed
)

private val MediCaseDarkColors = darkColorScheme(
    primary = ElectricBlueLight,
    onPrimary = DarkBackground,
    primaryContainer = ElectricBlueDarkContainer,
    onPrimaryContainer = ElectricBlueLight,
    secondary = ElectricBlueLight,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = DarkSurface,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurface,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = AlertRedLight,
    onError = DarkBackground,
    errorContainer = AlertRedDarkContainer,
    onErrorContainer = AlertRedLight
)

// ============================================================
//  Colores de estado clínico (no existen en Material 3)
// ============================================================
@Immutable
data class MediCaseStatusColors(
    val safe: Color,
    val safeContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val critical: Color,
    val criticalContainer: Color
)

private val LightStatusColors = MediCaseStatusColors(
    safe = SafeGreen,
    safeContainer = SafeGreenContainer,
    warning = WarningAmber,
    warningContainer = WarningAmberContainer,
    critical = AlertRed,
    criticalContainer = AlertRedContainer
)

private val DarkStatusColors = MediCaseStatusColors(
    safe = SafeGreenLight,
    safeContainer = SafeGreenDarkContainer,
    warning = WarningAmberLight,
    warningContainer = WarningAmberDarkContainer,
    critical = AlertRedLight,
    criticalContainer = AlertRedDarkContainer
)

private val LocalMediCaseStatusColors = staticCompositionLocalOf { LightStatusColors }

// ============================================================
//  Tema raíz de la aplicación
// ============================================================
@Composable
fun MediCaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MediCaseDarkColors else MediCaseLightColors
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(LocalMediCaseStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography, // Definida en Type.kt
            content = content
        )
    }
}

/**
 * Tema de la app en marcha: aplica la apariencia que eligió el usuario
 * (Sistema / Claro / Oscuro) y pone los íconos de las barras del sistema en
 * el tono que se lee sobre ella.
 *
 * Las barras siguen por defecto al teléfono, no a la app: sin este ajuste, la
 * app en oscuro sobre un teléfono en claro dejaría la hora y la batería en
 * negro sobre negro.
 */
@Composable
fun MediCaseAppearanceTheme(
    viewModel: AppearanceViewModel = viewModel(factory = AppearanceViewModel.Factory),
    content: @Composable () -> Unit
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val darkTheme = mode.isDark(systemIsDark = isSystemInDarkTheme())

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MediCaseTheme(darkTheme = darkTheme, content = content)
}

/** La Activity que hay detrás de un contexto, que puede venir envuelto. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Acceso a los colores de estado: MediCaseTheme.statusColors.critical */
object MediCaseTheme {
    val statusColors: MediCaseStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMediCaseStatusColors.current
}