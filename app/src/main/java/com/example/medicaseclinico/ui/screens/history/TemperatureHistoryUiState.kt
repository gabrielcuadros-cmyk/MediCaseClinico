package com.example.medicaseclinico.ui.screens.history

import androidx.compose.runtime.Immutable
import com.example.medicaseclinico.domain.model.HistoryEvent
import com.example.medicaseclinico.domain.model.HistoryRange
import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureStats
import com.example.medicaseclinico.domain.model.TemperatureStatus

/**
 * Estado único e inmutable de la pantalla Historial Térmico.
 *
 * Todos los estados llevan [selectedRange] porque el selector
 * "Últimas 24h · 7 Días · 30 Días" sigue visible mientras se carga, cuando el
 * rango no tiene lecturas y cuando hay un error: el usuario siempre puede
 * cambiar de rango.
 */
@Immutable
sealed interface TemperatureHistoryUiState {
    val selectedRange: HistoryRange

    /** Aún no hay respuesta de Room para el rango elegido. */
    data class Loading(
        override val selectedRange: HistoryRange
    ) : TemperatureHistoryUiState

    /**
     * El rango elegido no tiene lecturas.
     *
     * @param current última temperatura conocida aunque sea anterior al rango,
     * o null si el estuche nunca ha reportado (la tarjeta muestra "--").
     */
    data class Empty(
        override val selectedRange: HistoryRange,
        val current: CurrentTemperature?
    ) : TemperatureHistoryUiState

    /**
     * Hay lecturas reales en el rango.
     *
     * @param buckets lecturas resumidas por intervalos de `selectedRange.bucketMs`,
     * en orden cronológico, para la gráfica de estabilidad.
     * @param events línea de eventos, del más reciente al más antiguo.
     * @param rangeStart inicio de la ventana mostrada (epoch ms), para el eje de la gráfica.
     * @param rangeEnd fin de la ventana mostrada (epoch ms).
     */
    data class Success(
        override val selectedRange: HistoryRange,
        val current: CurrentTemperature,
        val buckets: List<TemperatureBucket>,
        val stats: TemperatureStats,
        val events: List<HistoryEvent>,
        val rangeStart: Long,
        val rangeEnd: Long
    ) : TemperatureHistoryUiState

    /** Fallo controlado al leer el historial. La pantalla ofrece reintentar. */
    data class Error(
        override val selectedRange: HistoryRange
    ) : TemperatureHistoryUiState
}

/**
 * Estado de "Exportar Reporte Médico (PDF)".
 *
 * Va separado de [TemperatureHistoryUiState] porque el historial se refresca
 * solo cada pocos segundos: si viajaran juntos, un refresco podría borrar el
 * aviso de que el reporte ya está listo o de que falló.
 */
@Immutable
sealed interface ReportExportState {
    /** No hay ninguna exportación en curso. */
    data object Idle : ReportExportState

    /** Se está generando el PDF: el botón muestra progreso y no se puede repetir. */
    data object Exporting : ReportExportState

    /** El PDF está listo en [uri]; la pantalla debe abrir el menú de compartir. */
    data class Ready(val uri: String) : ReportExportState

    /** No se pudo generar o compartir el PDF; [reason] decide el mensaje. */
    data class Failed(val reason: ReportExportError) : ReportExportState
}

/** Motivo del fallo, como tipo y no como texto, para traducirlo en la UI. */
enum class ReportExportError {
    /** No se pudo escribir el archivo PDF. */
    GENERATION,

    /** El PDF existe pero ninguna app del teléfono puede recibirlo. */
    NO_SHARE_APP
}

/**
 * Última temperatura registrada, ya clasificada, para la tarjeta
 * "Temperatura actual" y su chip de estado.
 *
 * @param timestamp momento de la lectura (epoch ms); permite a la UI avisar
 * si el dato no es reciente.
 */
@Immutable
data class CurrentTemperature(
    val celsius: Float,
    val status: TemperatureStatus,
    val timestamp: Long
)
