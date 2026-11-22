package com.example.medicaseclinico.ui.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.example.medicaseclinico.MediCaseApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Widget de la pantalla de inicio "Estado de la insulina": temperatura del
 * estuche en tiempo real y accesos directos a Historial, Mapa y Alertas.
 *
 * Los datos salen de los mismos repositorios que la app (Room y el enlace con
 * el estuche), nunca de valores fijos. Glance recolecta el flujo mientras su
 * sesión está abierta, así que el widget cambia en cuanto llega una lectura.
 */
class MediCaseWidget : GlanceAppWidget() {

    /** Un diseño por tamaño: el launcher elige el más grande que cabe. */
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, FULL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // El receptor corre después de Application.onCreate: el contenedor ya existe.
        val source = (context.applicationContext as? MediCaseApplication)
            ?.container
            ?.let(CaseWidgetStateSource::from)

        provideContent {
            var attempt by remember { mutableIntStateOf(0) }
            // Cada intento crea un flujo nuevo: tras un error se vuelve a leer desde cero.
            val states: Flow<CaseWidgetUiState> = remember(attempt) {
                source?.uiState() ?: flowOf(CaseWidgetUiState.Error)
            }
            val state by states.collectAsState(initial = CaseWidgetUiState.Loading)

            MediCaseWidgetTheme {
                MediCaseWidgetContent(state = state, onRetry = { attempt++ })
            }
        }
    }

    companion object {
        /** 2×2 celdas: temperatura y estado. */
        val COMPACT = DpSize(110.dp, 110.dp)

        /** 4×2 celdas: además, enlace, hora de la lectura y accesos directos. */
        val FULL = DpSize(250.dp, 170.dp)
    }
}

/** Punto de entrada que el sistema llama para crear y actualizar el widget. */
class MediCaseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediCaseWidget()
}
