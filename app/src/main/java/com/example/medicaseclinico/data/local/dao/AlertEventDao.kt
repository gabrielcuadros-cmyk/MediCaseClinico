package com.example.medicaseclinico.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.medicaseclinico.data.local.entity.AlertEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a la tabla "alert_events".
 *
 * Las consultas de observación devuelven `Flow`: Room re-emite al insertar
 * una alerta, así la lista y el contador se actualizan solos.
 */
@Dao
interface AlertEventDao {

    /** Guarda una alerta emitida. Devuelve el id generado. */
    @Insert
    suspend fun insertAlertEvent(event: AlertEventEntity): Long

    /** Todas las alertas, más recientes primero → pantalla de Alertas. */
    @Query("SELECT * FROM alert_events ORDER BY timestamp DESC")
    fun observeAlertEvents(): Flow<List<AlertEventEntity>>

    /** Alertas que el usuario aún no ha visto → indicador de la pestaña Alertas. */
    @Query("SELECT COUNT(*) FROM alert_events WHERE is_read = 0")
    fun observeUnreadCount(): Flow<Int>

    /** Marca todas las alertas como vistas. */
    @Query("UPDATE alert_events SET is_read = 1 WHERE is_read = 0")
    suspend fun markAllAsRead()
}
