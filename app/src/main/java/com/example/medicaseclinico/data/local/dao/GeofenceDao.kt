package com.example.medicaseclinico.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.medicaseclinico.data.local.entity.GeofenceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a la tabla "geofences".
 */
@Dao
interface GeofenceDao {

    /** Todas las zonas, en el orden en que se crearon → lista "Zonas guardadas". */
    @Query("SELECT * FROM geofences ORDER BY id ASC")
    fun observeGeofences(): Flow<List<GeofenceEntity>>

    /**
     * Crea la zona, o la actualiza si su id ya existe.
     *
     * @return el id generado al crear, o -1 si fue una actualización.
     */
    @Upsert
    suspend fun upsertGeofence(geofence: GeofenceEntity): Long

    /** Borra una zona. Devuelve cuántas filas se borraron (0 si no existía). */
    @Query("DELETE FROM geofences WHERE id = :id")
    suspend fun deleteGeofence(id: Long): Int
}
