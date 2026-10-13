package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.LossModeState
import kotlinx.coroutines.flow.Flow

/** Persistencia del Modo Pérdida. */
interface LossModeRepository {
    /** Emite el estado guardado y cada cambio posterior. */
    val state: Flow<LossModeState>

    /**
     * Actualiza de forma atómica: [transform] recibe el valor actual y devuelve el nuevo.
     */
    suspend fun update(transform: (LossModeState) -> LossModeState): Result<Unit>
}
