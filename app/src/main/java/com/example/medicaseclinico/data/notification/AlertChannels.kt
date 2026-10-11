package com.example.medicaseclinico.data.notification

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationManagerCompat
import com.example.medicaseclinico.R

/**
 * Canales de notificación de MediCase Clínico, uno por cada tipo de alerta
 * de la pantalla "Notificaciones" del diseño.
 */
enum class AlertChannel(
    val id: String,
    @get:StringRes val nameRes: Int,
    @get:StringRes val descriptionRes: Int,
    val importance: Int,
    val isCritical: Boolean
) {
    TEMPERATURE(
        id = "medicase_temperature_alerts",
        nameRes = R.string.channel_temperature_name,
        descriptionRes = R.string.channel_temperature_description,
        importance = NotificationManagerCompat.IMPORTANCE_HIGH,
        isCritical = true
    ),
    BATTERY(
        id = "medicase_battery_alerts",
        nameRes = R.string.channel_battery_name,
        descriptionRes = R.string.channel_battery_description,
        importance = NotificationManagerCompat.IMPORTANCE_DEFAULT,
        isCritical = false
    ),
    CONNECTION(
        id = "medicase_connection_alerts",
        nameRes = R.string.channel_connection_name,
        descriptionRes = R.string.channel_connection_description,
        importance = NotificationManagerCompat.IMPORTANCE_DEFAULT,
        isCritical = false
    ),
    GEOFENCE(
        id = "medicase_geofence_alerts",
        nameRes = R.string.channel_geofence_name,
        descriptionRes = R.string.channel_geofence_description,
        importance = NotificationManagerCompat.IMPORTANCE_HIGH,
        isCritical = true
    ),
    SECURITY(
        id = "medicase_security_alerts",
        nameRes = R.string.channel_security_name,
        descriptionRes = R.string.channel_security_description,
        importance = NotificationManagerCompat.IMPORTANCE_HIGH,
        isCritical = true
    );

    companion object {
        private const val GROUP_ID = "medicase_case_alerts"
        private val CRITICAL_VIBRATION = longArrayOf(0, 500, 250, 500, 250, 500)

        fun createAll(context: Context) {
            try {
                val manager = NotificationManagerCompat.from(context)
                manager.createNotificationChannelGroup(
                    NotificationChannelGroupCompat.Builder(GROUP_ID)
                        .setName(context.getString(R.string.channel_group_name))
                        .build()
                )
                val channels = entries.map { channel ->
                    NotificationChannelCompat.Builder(channel.id, channel.importance)
                        .setName(context.getString(channel.nameRes))
                        .setDescription(context.getString(channel.descriptionRes))
                        .setGroup(GROUP_ID)
                        .setShowBadge(true)
                        .apply {
                            if (channel.isCritical) {
                                setVibrationEnabled(true)
                                setVibrationPattern(CRITICAL_VIBRATION)
                                setLightsEnabled(true)
                                setLightColor(Color.RED)
                            }
                        }
                        .build()
                }
                manager.createNotificationChannelsCompat(channels)
            } catch (e: Exception) {
                // Si falla la creación de canales, la app sigue operativa
            }
        }
    }
}