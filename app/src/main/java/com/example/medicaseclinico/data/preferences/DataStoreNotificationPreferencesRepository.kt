package com.example.medicaseclinico.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.medicaseclinico.domain.model.NotificationPreferences
import com.example.medicaseclinico.domain.repository.NotificationPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private val Context.notificationDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "medicase_notification_preferences")

class DataStoreNotificationPreferencesRepository(
    context: Context
) : NotificationPreferencesRepository {
    private val dataStore = context.applicationContext.notificationDataStore

    override val preferences: Flow<NotificationPreferences> =
        dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .map { stored -> stored.toDomain() }

    override suspend fun update(
        transform: (NotificationPreferences) -> NotificationPreferences
    ): Result<Unit> = try {
        dataStore.edit { stored ->
            val updated = transform(stored.toDomain())
            stored.writeFrom(updated)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun Preferences.toDomain(): NotificationPreferences {
        val defaults = NotificationPreferences()
        return NotificationPreferences(
            temperatureAlertsEnabled = this[Keys.TEMPERATURE] ?: defaults.temperatureAlertsEnabled,
            batteryAlertsEnabled = this[Keys.BATTERY] ?: defaults.batteryAlertsEnabled,
            connectionAlertsEnabled = this[Keys.CONNECTION] ?: defaults.connectionAlertsEnabled,
            geofenceAlertsEnabled = this[Keys.GEOFENCE] ?: defaults.geofenceAlertsEnabled,
            securityAlertsEnabled = this[Keys.SECURITY] ?: defaults.securityAlertsEnabled,
            temporarilySilenced = this[Keys.SILENCED] ?: defaults.temporarilySilenced,
            quietHoursEnabled = this[Keys.QUIET_ENABLED] ?: defaults.quietHoursEnabled,
            quietHoursStartMinute = (this[Keys.QUIET_START] ?: defaults.quietHoursStartMinute)
                .coerceIn(0, NotificationPreferences.MINUTES_PER_DAY - 1),
            quietHoursEndMinute = (this[Keys.QUIET_END] ?: defaults.quietHoursEndMinute)
                .coerceIn(0, NotificationPreferences.MINUTES_PER_DAY - 1)
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.writeFrom(
        prefs: NotificationPreferences
    ) {
        this[Keys.TEMPERATURE] = prefs.temperatureAlertsEnabled
        this[Keys.BATTERY] = prefs.batteryAlertsEnabled
        this[Keys.CONNECTION] = prefs.connectionAlertsEnabled
        this[Keys.GEOFENCE] = prefs.geofenceAlertsEnabled
        this[Keys.SECURITY] = prefs.securityAlertsEnabled
        this[Keys.SILENCED] = prefs.temporarilySilenced
        this[Keys.QUIET_ENABLED] = prefs.quietHoursEnabled
        this[Keys.QUIET_START] = prefs.quietHoursStartMinute
        this[Keys.QUIET_END] = prefs.quietHoursEndMinute
    }

    private object Keys {
        val TEMPERATURE = booleanPreferencesKey("alerts_temperature")
        val BATTERY = booleanPreferencesKey("alerts_battery")
        val CONNECTION = booleanPreferencesKey("alerts_connection")
        val GEOFENCE = booleanPreferencesKey("alerts_geofence")
        val SECURITY = booleanPreferencesKey("alerts_security")
        val SILENCED = booleanPreferencesKey("silenced_temporarily")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START = intPreferencesKey("quiet_hours_start_minute")
        val QUIET_END = intPreferencesKey("quiet_hours_end_minute")
    }
}