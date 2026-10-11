package com.example.medicaseclinico.ui.screens.history

import androidx.compose.runtime.Immutable
import com.example.medicaseclinico.domain.model.HistoryEvent
import com.example.medicaseclinico.domain.model.HistoryRange
import com.example.medicaseclinico.domain.model.TemperatureReading
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
     * @param readings lecturas en orden cronológico para la gráfica de estabilidad.
     * @param events línea de eventos, del más reciente al más antiguo.
     * @param rangeStart inicio de la ventana mostrada (epoch ms), para el eje de la gráfica.
     * @param rangeEnd fin de la ventana mostrada (epoch ms).
     */
    data class Success(
        override val selectedRange: HistoryRange,
        val current: CurrentTemperature,
        val readings: List<TemperatureReading>,
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
