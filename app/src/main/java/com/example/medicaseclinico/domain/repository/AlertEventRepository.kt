package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.AlertEvent
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del historial de alertas emitidas por la app.
 *
 * Mismas reglas que el resto de repositorios: los `Flow` emiten listas vacías
 * cuando no hay datos (nunca valores inventados) y las operaciones `suspend`
 * devuelven `Result` para que el llamador decida cómo tratar el fallo.
 */
interface AlertEventRepository {

    /** Todas las alertas, de la más reciente a la más antigua → pantalla de Alertas. */
    fun observeAlertEvents(): Flow<List<AlertEvent>>

    /** Cuántas alertas no ha visto el usuario → indicador de la pestaña Alertas. */
    fun observeUnreadCount(): Flow<Int>

    /** Guarda una alerta recién emitida. Success contiene el id generado. */
    suspend fun recordAlert(event: AlertEvent): Result<Long>

    /** Marca todas como vistas al abrir la lista de alertas. */
    suspend fun markAllAsRead(): Result<Unit>
}
