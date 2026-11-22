package com.example.medicaseclinico.ui.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Mantiene un widget de la pantalla de inicio al día mientras la app está en
 * memoria.
 *
 * Glance solo recolecta datos durante una sesión corta; sin esto el widget se
 * quedaría con lo último que vio hasta el refresco periódico del sistema
 * (30 min). Solo redibuja si cambia lo que el widget muestra: [changes] debe
 * emitir únicamente el contenido visible (p. ej. la hora al minuto), así una
 * lectura cada 10 s no despierta al launcher cada 10 s.
 *
 * @param widget widget que se redibuja (todas sus copias en la pantalla).
 * @param changes contenido visible del widget, a partir de datos reales.
 */
class WidgetRefresher(
    private val context: Context,
    private val widget: GlanceAppWidget,
    private val changes: Flow<Any>,
    private val scope: CoroutineScope
) {
    /** Empieza a observar; la primera emisión corrige un widget que quedó de otra sesión. */
    fun start() {
        scope.launch {
            changes
                .distinctUntilChanged()
                // Si el historial falla, el widget lo mostrará en su siguiente sesión.
                .catch { error -> if (error is CancellationException) throw error }
                .collect { refreshPlacedWidgets() }
        }
    }

    private suspend fun refreshPlacedWidgets() {
        try {
            val placed = GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass)
            if (placed.isNotEmpty()) widget.updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // El launcher puede rechazar la actualización (p. ej. al reiniciarse):
            // se reintenta con el siguiente dato y nunca debe tumbar la app.
        }
    }
}
