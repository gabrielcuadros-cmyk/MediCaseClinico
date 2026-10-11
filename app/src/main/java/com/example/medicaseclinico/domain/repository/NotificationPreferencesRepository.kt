package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.NotificationPreferences
import kotlinx.coroutines.flow.Flow

/** Persistencia de las preferencias de alertas del usuario. */
interface NotificationPreferencesRepository {
    /** Emite las preferencias actuales y cada cambio posterior. */
    val preferences: Flow<NotificationPreferences>

    /**
     * Actualiza de forma atómica: [transform] recibe el valor actual y devuelve el nuevo.
     */
    suspend fun update(
        transform: (NotificationPreferences) -> NotificationPreferences
    ): Result<Unit>
}