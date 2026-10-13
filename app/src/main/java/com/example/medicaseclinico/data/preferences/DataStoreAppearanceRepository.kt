package com.example.medicaseclinico.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.medicaseclinico.domain.model.AppearanceMode
import com.example.medicaseclinico.domain.repository.AppearanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private val Context.appearanceDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "medicase_appearance")

/**
 * Apariencia guardada en DataStore. Se guarda el nombre del modo y no su
 * posición, para que reordenar el enum no cambie lo que eligió el usuario.
 */
class DataStoreAppearanceRepository(
    context: Context
) : AppearanceRepository {
    private val dataStore = context.applicationContext.appearanceDataStore

    override val mode: Flow<AppearanceMode> =
        dataStore.data
            .catch { error ->
                // Archivo ilegible: se sigue al teléfono en lugar de fallar.
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .map { stored -> AppearanceMode.fromStored(stored[MODE]) }
            .distinctUntilChanged()

    override suspend fun setMode(mode: AppearanceMode): Result<Unit> = try {
        dataStore.edit { stored -> stored[MODE] = mode.name }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        val MODE = stringPreferencesKey("appearance_mode")
    }
}
