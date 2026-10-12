package com.example.medicaseclinico.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.medicaseclinico.data.local.entity.CaseLocationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a la tabla "case_locations".
 */
@Dao
interface CaseLocationDao {

    /** Guarda una posición del estuche. Devuelve el id generado. */
    @Insert
    suspend fun insertLocation(location: CaseLocationEntity): Long

    /**
     * Última posición guardada, o null si el estuche nunca ha informado una.
     * Room re-emite al insertar, así el mapa se mueve solo.
     */
    @Query("SELECT * FROM case_locations ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestLocation(): Flow<CaseLocationEntity?>

    /** Borra las posiciones anteriores a [timestamp]. Devuelve cuántas se borraron. */
    @Query("DELETE FROM case_locations WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int
}
