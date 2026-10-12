package com.example.medicaseclinico.data.repository

import com.example.medicaseclinico.data.local.dao.GeofenceDao
import com.example.medicaseclinico.data.mapper.toDomain
import com.example.medicaseclinico.data.mapper.toEntity
import com.example.medicaseclinico.domain.model.Geofence
import com.example.medicaseclinico.domain.repository.GeofenceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Zonas de seguridad respaldadas por Room.
 *
 * @param ioDispatcher hilo de fondo para E/S de disco; se inyecta para poder
 * reemplazarlo por un dispatcher de prueba en los tests.
 */
class GeofenceRepositoryImpl(
    private val dao: GeofenceDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : GeofenceRepository {

    override fun observeGeofences(): Flow<List<Geofence>> =
        dao.observeGeofences()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun saveGeofence(geofence: Geofence): Result<Long> =
        safeDbCall {
            val insertedId = dao.upsertGeofence(geofence.toEntity())
            // Room devuelve -1 cuando actualizó una zona que ya existía.
            if (insertedId == UPDATED_ROW_ID) geofence.id else insertedId
        }

    override suspend fun deleteGeofence(id: Long): Result<Unit> =
        safeDbCall { dao.deleteGeofence(id) }.map { }

    private suspend inline fun <T> safeDbCall(
        crossinline block: suspend () -> T
    ): Result<T> = withContext(ioDispatcher) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val UPDATED_ROW_ID = -1L
    }
}
