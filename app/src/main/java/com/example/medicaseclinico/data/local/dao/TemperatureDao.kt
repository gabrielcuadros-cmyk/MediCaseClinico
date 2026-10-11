package com.example.medicaseclinico.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.medicaseclinico.data.local.entity.TemperatureEntity
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a la tabla "temperature_readings".
 *
 * - Escrituras y consultas puntuales: funciones `suspend` (Room las ejecuta
 *   fuera del hilo principal automáticamente).
 * - Observación en tiempo real: funciones que devuelven `Flow`. Room re-emite
 *   cada vez que la tabla cambia, por lo que la UI se actualiza sola cuando
 *   llega una nueva lectura BLE.
 */
@Dao
interface TemperatureDao {

    // ----------------------------------------------------------------------
    //  ESCRITURA (suspend)
    // ----------------------------------------------------------------------

    /** Guarda una lectura recibida del estuche. Devuelve el id generado. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: TemperatureEntity): Long

    /** Inserción por lotes: útil al sincronizar lecturas acumuladas tras una desconexión BLE. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(readings: List<TemperatureEntity>)

    /**
     * Elimina lecturas anteriores a [olderThan] (epoch ms) para que la base
     * no crezca sin límite. Devuelve cuántas filas se borraron.
     */
    @Query("DELETE FROM temperature_readings WHERE timestamp < :olderThan")
    suspend fun deleteReadingsOlderThan(olderThan: Long): Int

    // ----------------------------------------------------------------------
    //  OBSERVACIÓN EN TIEMPO REAL (Flow)
    // ----------------------------------------------------------------------

    /**
     * Última lectura registrada → tarjeta central de 4.2 °C del Dashboard.
     * Emite null si la tabla está vacía (habilita el Empty State en la UI).
     */
    @Query("SELECT * FROM temperature_readings ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestReading(): Flow<TemperatureEntity?>

    /**
     * Lecturas desde [since] (epoch ms) en orden cronológico → gráfica de
     * estabilidad 2–8 °C con filtros Últimas 24h / 7 Días / 30 Días.
     */
    @Query(
        """
        SELECT * FROM temperature_readings 
        WHERE timestamp >= :since 
        ORDER BY timestamp ASC
        """
    )
    fun observeReadingsSince(since: Long): Flow<List<TemperatureEntity>>

    /** Solo lecturas que dispararon alerta, más recientes primero → pantalla de Alertas. */
    @Query(
        """
        SELECT * FROM temperature_readings 
        WHERE is_alert_triggered = 1 
        ORDER BY timestamp DESC
        """
    )
    fun observeAlertReadings(): Flow<List<TemperatureEntity>>

    /**
     * Último nivel de batería conocido (ignora muestras sin batería) →
     * tarjeta del 78 % del Dashboard.
     */
    @Query(
        """
        SELECT battery_level FROM temperature_readings 
        WHERE battery_level IS NOT NULL 
        ORDER BY timestamp DESC 
        LIMIT 1
        """
    )
    fun observeLatestBatteryLevel(): Flow<Int?>

    // ----------------------------------------------------------------------
    //  CONSULTAS PUNTUALES (suspend)
    // ----------------------------------------------------------------------

    /** Lecturas de un rango cerrado, p. ej. para exportar el Reporte Médico (PDF). */
    @Query(
        """
        SELECT * FROM temperature_readings 
        WHERE timestamp BETWEEN :from AND :to 
        ORDER BY timestamp ASC
        """
    )
    suspend fun getReadingsBetween(from: Long, to: Long): List<TemperatureEntity>

    /** Total de lecturas almacenadas (diagnóstico y tests). */
    @Query("SELECT COUNT(*) FROM temperature_readings")
    suspend fun getReadingsCount(): Int
}