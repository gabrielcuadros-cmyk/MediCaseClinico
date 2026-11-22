package com.example.medicaseclinico.ui.screens.alerts

import androidx.compose.runtime.Immutable
import com.example.medicaseclinico.domain.model.AlertFilter
import com.example.medicaseclinico.domain.model.AlertType
import com.example.medicaseclinico.domain.model.RelativeTime

/**
 * Estado único e inmutable de la pantalla "Alertas de Seguridad".
 *
 * [Empty] y [Success] llevan el filtro elegido porque los chips siguen
 * visibles en ambos: un filtro sin alertas no impide cambiar a otro.
 */
@Immutable
sealed interface AlertsUiState {

    /** Aún no hay respuesta de Room. */
    data object Loading : AlertsUiState

    /**
     * No hay alertas para [selectedFilter]: o no se ha emitido ninguna, o
     * ninguna es de esa categoría.
     */
    data class Empty(
        val selectedFilter: AlertFilter,
        val filters: List<AlertFilter> = AlertFilter.entries
    ) : AlertsUiState

    /**
     * @param alerts alertas del filtro, de la más reciente a la más antigua.
     * @param filters chips que puede usar la cuenta (un invitado, solo los de sus permisos).
     */
    data class Success(
        val selectedFilter: AlertFilter,
        val alerts: List<AlertItem>,
        val filters: List<AlertFilter> = AlertFilter.entries
    ) : AlertsUiState

    /** Fallo controlado al leer el historial. La pantalla ofrece reintentar. */
    data object Error : AlertsUiState
}

/**
 * Una tarjeta de la lista.
 *
 * Lleva el tipo y el valor medido, no los textos: el título, la descripción,
 * el ícono y el color los decide la UI con sus recursos.
 *
 * @param value temperatura en °C o batería en % según [type]; null si el tipo
 * no lleva valor.
 * @param elapsed tiempo desde la alerta, ya calculado para no hacerlo al dibujar.
 * @param dateTime fecha y hora exactas ("21 nov 2026, 18:04"): "Hace 3 días"
 * no basta para un registro de accesos ni para revisar cuándo pasó algo.
 * @param detail nombre de la zona en una alerta de geocerca; null si la alerta
 * no lo tiene (otros tipos, o geocercas registradas sin nombre).
 */
@Immutable
data class AlertItem(
    val id: Long,
    val type: AlertType,
    val value: Float?,
    val elapsed: RelativeTime,
    val dateTime: String,
    val detail: String? = null
)
