package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.CaseLocation
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del historial de posiciones del estuche.
 *
 * Responsabilidades separadas:
 *  - CaseDeviceRepository   → lo que el GPS del estuche dice ahora (BLE).
 *  - CaseLocationRepository → lo que quedó guardado (Room), disponible
 *    aunque no haya enlace.
 *
 * Mismas reglas que el resto de repositorios: el `Flow` emite null cuando no
 * hay datos (nunca una posición inventada) y las operaciones `suspend`
 * devuelven `Result`.
 */
interface CaseLocationRepository {

    /** Última ubicación conocida del estuche, o null si nunca informó una. */
    fun observeLastKnownLocation(): Flow<CaseLocation?>

    /** Guarda una posición recibida del estuche. Success contiene el id generado. */
    suspend fun saveLocation(location: CaseLocation): Result<Long>

    /** Borra las posiciones anteriores a [timestamp]. Success contiene cuántas se borraron. */
    suspend fun deleteOlderThan(timestamp: Long): Result<Int>
}
