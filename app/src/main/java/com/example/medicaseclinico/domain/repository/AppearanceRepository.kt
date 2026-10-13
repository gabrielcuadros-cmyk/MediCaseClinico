package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.AppearanceMode
import kotlinx.coroutines.flow.Flow

/** Persistencia de la apariencia elegida (Sistema / Claro / Oscuro). */
interface AppearanceRepository {
    /** Emite la apariencia guardada y cada cambio posterior. */
    val mode: Flow<AppearanceMode>

    suspend fun setMode(mode: AppearanceMode): Result<Unit>
}
