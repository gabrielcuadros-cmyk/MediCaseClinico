package com.example.medicaseclinico.data.mqtt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlin.coroutines.cancellation.CancellationException

private val Context.mqttDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "medicase_mqtt")

/** Dónde se guarda el Client ID de esta instalación. */
interface MqttClientIdStore {
    /**
     * El Client ID guardado, o uno nuevo que queda guardado para las siguientes
     * conexiones. Falla solo si no se puede leer ni escribir el almacenamiento.
     */
    suspend fun getOrCreate(): Result<String>
}

/**
 * Client ID en DataStore: se genera la primera vez y se reutiliza siempre, para que
 * la app se presente igual en cada reconexión.
 */
class DataStoreMqttClientIdStore(
    context: Context
) : MqttClientIdStore {
    private val dataStore = context.applicationContext.mqttDataStore

    override suspend fun getOrCreate(): Result<String> = try {
        var clientId = ""
        // Leer y escribir en la misma edición: dos llamadas a la vez no generan dos ID.
        dataStore.edit { stored ->
            val current = stored[CLIENT_ID]
            clientId = if (current != null && MqttClientId.isValidAppClientId(current)) {
                current
            } else {
                MqttClientId.newAppClientId().also { stored[CLIENT_ID] = it }
            }
        }
        Result.success(clientId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        val CLIENT_ID = stringPreferencesKey("mqtt_client_id")
    }
}
