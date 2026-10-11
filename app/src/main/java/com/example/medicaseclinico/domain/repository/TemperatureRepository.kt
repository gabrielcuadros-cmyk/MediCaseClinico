package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.TemperatureReading
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de acceso al historial térmico de MediCase Clínico.
 *
 * Reglas del contrato:
 * - Los `Flow` son la fuente en tiempo real para la UI. Emiten listas vacías
 *   o `null` cuando no hay datos (nunca valores inventados), lo que permite
 *   mostrar el Empty State.
 * - Las operaciones `suspend` devuelven `Result<T>`: el llamador recibe
 *   Success o Failure y decide cómo mostrar el error, sin try-catch disperso
 *   y sin crashes.
 */
interface TemperatureRepository {

    // ------------------------------------------------------------------
    //  Observación en tiempo real
    // ------------------------------------------------------------------

    /** Última lectura del estuche → tarjeta central del Dashboard (4.2 °C). */
    fun observeLatestReading(): Flow<TemperatureReading?>

    /** Lecturas desde [since] (epoch ms) → gráfica 24h / 7 días / 30 días. */
    fun observeReadingsSince(since: Long): Flow<List<TemperatureReading>>

    /** Lecturas fuera de rango → pantalla de Alertas. */
    fun observeAlertReadings(): Flow<List<TemperatureReading>>

    /** Último nivel de batería conocido → tarjeta de batería (78 %). */
    fun observeLatestBatteryLevel(): Flow<Int?>

    // ------------------------------------------------------------------
    //  Escritura y consultas puntuales
    // ------------------------------------------------------------------

    /** Guarda una lectura recibida por BLE. Success contiene el id generado. */
    suspend fun saveReading(reading: TemperatureReading): Result<Long>

    /** Sincroniza el buffer de lecturas acumuladas tras una reconexión BLE. */
    suspend fun saveReadings(readings: List<TemperatureReading>): Result<Unit>

    /** Lecturas de un rango cerrado → Exportar Reporte Médico (PDF). */
    suspend fun getReadingsBetween(from: Long, to: Long): Result<List<TemperatureReading>>

    /** Purga lecturas antiguas. Success contiene cuántas se eliminaron. */
    suspend fun purgeReadingsOlderThan(olderThan: Long): Result<Int>
}