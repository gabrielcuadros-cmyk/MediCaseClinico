package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.Geofence
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de las zonas de seguridad guardadas en el teléfono.
 *
 * Mismas reglas que el resto de repositorios: el `Flow` emite una lista vacía
 * cuando no hay zonas y las operaciones `suspend` devuelven `Result`.
 */
interface GeofenceRepository {

    /** Todas las zonas, en el orden en que se crearon. */
    fun observeGeofences(): Flow<List<Geofence>>

    /**
     * Crea la zona si su id es 0, o la actualiza si ya existe.
     * Success contiene el id de la zona guardada.
     */
    suspend fun saveGeofence(geofence: Geofence): Result<Long>

    /** Borra una zona. No falla si ya no existía. */
    suspend fun deleteGeofence(id: Long): Result<Unit>
}
