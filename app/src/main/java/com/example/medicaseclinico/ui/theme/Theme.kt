package com.example.medicaseclinico.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

/** Acceso a los colores de estado: MediCaseTheme.statusColors.critical */
object MediCaseTheme {
    val statusColors: MediCaseStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMediCaseStatusColors.current
}