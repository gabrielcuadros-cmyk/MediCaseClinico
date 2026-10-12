package com.example.medicaseclinico.data.repository

import com.example.medicaseclinico.data.local.dao.CaseLocationDao
import com.example.medicaseclinico.data.mapper.toDomain
import com.example.medicaseclinico.data.mapper.toEntity
import com.example.medicaseclinico.domain.model.CaseLocation
import com.example.medicaseclinico.domain.repository.CaseLocationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Historial de posiciones del estuche respaldado por Room.
 *
 * @param ioDispatcher hilo de fondo para E/S de disco; se inyecta para poder
 * reemplazarlo por un dispatcher de prueba en los tests.
 */
class CaseLocationRepositoryImpl(
    private val dao: CaseLocationDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CaseLocationRepository {

    override fun observeLastKnownLocation(): Flow<CaseLocation?> =
        dao.observeLatestLocation()
            .map { entity -> entity?.toDomain() }
            .flowOn(ioDispatcher)

    override suspend fun saveLocation(location: CaseLocation): Result<Long> =
        safeDbCall { dao.insertLocation(location.toEntity()) }

    override suspend fun deleteOlderThan(timestamp: Long): Result<Int> =
        safeDbCall { dao.deleteOlderThan(timestamp) }

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
}
