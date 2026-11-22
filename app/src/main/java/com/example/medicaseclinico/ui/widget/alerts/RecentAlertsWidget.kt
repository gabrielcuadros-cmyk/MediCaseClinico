package com.example.medicaseclinico.ui.widget.alerts

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.ui.widget.MediCaseWidgetTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Widget de la pantalla de inicio "Últimas alertas": lista de lo último que
 * ocurrió en el estuche, sacada del historial de alertas real (Room).
 *
 * Complementa al widget "Estado de la insulina": aquel dice cómo está la
 * insulina ahora; este, qué pasó hace poco.
 */
class RecentAlertsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // El receptor corre después de Application.onCreate: el contenedor ya existe.
        val source = (context.applicationContext as? MediCaseApplication)
            ?.container
            ?.let(RecentAlertsStateSource::from)

        provideContent {
            var attempt by remember { mutableIntStateOf(0) }
            // Cada intento crea un flujo nuevo: tras un error se vuelve a leer desde cero.
            val states: Flow<RecentAlertsUiState> = remember(attempt) {
                source?.uiState() ?: flowOf(RecentAlertsUiState.Error)
            }
            val state by states.collectAsState(initial = RecentAlertsUiState.Loading)

            MediCaseWidgetTheme {
                RecentAlertsWidgetContent(state = state, onRetry = { attempt++ })
            }
        }
    }
}

/** Punto de entrada que el sistema llama para crear y actualizar el widget. */
class RecentAlertsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentAlertsWidget()
}
