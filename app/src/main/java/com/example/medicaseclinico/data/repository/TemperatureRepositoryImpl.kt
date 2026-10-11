package com.example.medicaseclinico.data.repository

import com.example.medicaseclinico.data.local.dao.TemperatureDao
import com.example.medicaseclinico.data.mapper.toDomain
import com.example.medicaseclinico.data.mapper.toEntity
import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.repository.TemperatureRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Implementación del historial térmico respaldada por Room.
 *
 * @param dao acceso a la tabla temperature_readings (inyección por constructor).
 * @param ioDispatcher hilo de fondo para E/S de disco. Se inyecta para poder
 *                     reemplazarlo por un dispatcher de prueba en los tests unitarios.
 */
class TemperatureRepositoryImpl(
    private val dao: TemperatureDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TemperatureRepository {

    // ------------------------------------------------------------------
    //  Observación en tiempo real
    // ------------------------------------------------------------------
    override fun observeLatestReading(): Flow<TemperatureReading?> =
        dao.observeLatestReading()
            .map { entity -> entity?.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeReadingsSince(since: Long): Flow<List<TemperatureReading>> =
        dao.observeReadingsSince(since)
            .map { entities -> entities.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeBucketsSince(since: Long, bucketMs: Long): Flow<List<TemperatureBucket>> =
        dao.observeBucketsSince(
            since = since,
            bucketMs = bucketMs,
            minSafe = TemperatureStatus.MIN_SAFE_CELSIUS,
            maxSafe = TemperatureStatus.MAX_SAFE_CELSIUS
        )
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeAlertReadings(): Flow<List<TemperatureReading>> =
        dao.observeAlertReadings()
            .map { entities -> entities.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeLatestBatteryLevel(): Flow<Int?> =
        dao.observeLatestBatteryLevel()
            .flowOn(ioDispatcher)

    // ------------------------------------------------------------------
    //  Escritura y consultas puntuales
    // ------------------------------------------------------------------
    override suspend fun saveReading(reading: TemperatureReading): Result<Long> =
        safeDbCall {
            require(reading.temperatureCelsius.isFinite()) {
                "Lectura de temperatura inválida recibida del estuche"
            }
            dao.insertReading(reading.toEntity())
        }

    override suspend fun saveReadings(readings: List<TemperatureReading>): Result<Unit> =
        safeDbCall {
            if (readings.isNotEmpty()) {
                dao.insertReadings(readings.map { it.toEntity() })
            }
        }

    override suspend fun getReadingsBetween(
        from: Long,
        to: Long
    ): Result<List<TemperatureReading>> =
        safeDbCall {
            require(from <= to) { "Rango de fechas inválido: inicio posterior al fin" }
            dao.getReadingsBetween(from, to).toDomain()
        }

    override suspend fun purgeReadingsOlderThan(olderThan: Long): Result<Int> =
        safeDbCall { dao.deleteReadingsOlderThan(olderThan) }

    // ------------------------------------------------------------------
    //  Manejo seguro de errores
    // ------------------------------------------------------------------
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