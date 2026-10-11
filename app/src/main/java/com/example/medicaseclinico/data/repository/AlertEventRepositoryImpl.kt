package com.example.medicaseclinico.data.repository

import com.example.medicaseclinico.data.local.dao.AlertEventDao
import com.example.medicaseclinico.data.mapper.toDomain
import com.example.medicaseclinico.data.mapper.toEntity
import com.example.medicaseclinico.domain.model.AlertEvent
import com.example.medicaseclinico.domain.repository.AlertEventRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Historial de alertas respaldado por Room.
 *
 * @param ioDispatcher hilo de fondo para E/S de disco; se inyecta para poder
 * reemplazarlo por un dispatcher de prueba en los tests.
 */
class AlertEventRepositoryImpl(
    private val dao: AlertEventDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AlertEventRepository {

    override fun observeAlertEvents(): Flow<List<AlertEvent>> =
        dao.observeAlertEvents()
            .map { entities -> entities.mapNotNull { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeUnreadCount(): Flow<Int> =
        dao.observeUnreadCount()
            .flowOn(ioDispatcher)

    override suspend fun recordAlert(event: AlertEvent): Result<Long> =
        safeDbCall { dao.insertAlertEvent(event.toEntity()) }

    override suspend fun markAllAsRead(): Result<Unit> =
        safeDbCall { dao.markAllAsRead() }

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
