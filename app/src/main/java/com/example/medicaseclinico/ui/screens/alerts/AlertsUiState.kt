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
    data class Empty(val selectedFilter: AlertFilter) : AlertsUiState

    /** @param alerts alertas del filtro, de la más reciente a la más antigua. */
    data class Success(
        val selectedFilter: AlertFilter,
        val alerts: List<AlertItem>
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
 */
@Immutable
data class AlertItem(
    val id: Long,
    val type: AlertType,
    val value: Float?,
    val elapsed: RelativeTime
)
