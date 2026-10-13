package com.example.medicaseclinico.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.medicaseclinico.domain.model.LossModeState
import com.example.medicaseclinico.domain.repository.LossModeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private val Context.lossModeDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "medicase_loss_mode")

/**
 * Modo Pérdida guardado en DataStore: es un puñado de valores sueltos, no
 * necesita una tabla.
 */
class DataStoreLossModeRepository(
    context: Context
) : LossModeRepository {
    private val dataStore = context.applicationContext.lossModeDataStore

    override val state: Flow<LossModeState> =
        dataStore.data
            .catch { error ->
                // Archivo ilegible: se parte de "desactivado" en lugar de fallar.
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .map { stored -> stored.toDomain() }

    override suspend fun update(
        transform: (LossModeState) -> LossModeState
    ): Result<Unit> = try {
        dataStore.edit { stored ->
            stored.writeFrom(transform(stored.toDomain()))
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun Preferences.toDomain(): LossModeState {
        val active = this[Keys.ACTIVE] ?: false
        // Sin modo activo no se leen sus ayudas: no pueden quedar encendidas solas.
        if (!active) return LossModeState()
        return LossModeState(
            isActive = true,
            activatedAt = this[Keys.ACTIVATED_AT],
            alarmRequested = this[Keys.ALARM] ?: false,
            highFrequencyGpsRequested = this[Keys.HIGH_FREQUENCY_GPS] ?: false
        )
    }

    private fun MutablePreferences.writeFrom(state: LossModeState) {
        this[Keys.ACTIVE] = state.isActive
        this[Keys.ALARM] = state.alarmRequested
        this[Keys.HIGH_FREQUENCY_GPS] = state.highFrequencyGpsRequested
        val activatedAt = state.activatedAt
        if (activatedAt != null) this[Keys.ACTIVATED_AT] = activatedAt else remove(Keys.ACTIVATED_AT)
    }

    private object Keys {
        val ACTIVE = booleanPreferencesKey("loss_mode_active")
        val ACTIVATED_AT = longPreferencesKey("loss_mode_activated_at")
        val ALARM = booleanPreferencesKey("loss_mode_alarm_requested")
        val HIGH_FREQUENCY_GPS = booleanPreferencesKey("loss_mode_high_frequency_gps_requested")
    }
}
