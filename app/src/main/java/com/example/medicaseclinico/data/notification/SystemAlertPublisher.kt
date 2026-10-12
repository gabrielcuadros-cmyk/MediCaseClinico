package com.example.medicaseclinico.data.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.medicaseclinico.MainActivity
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.alert.AlertPublisher
import com.example.medicaseclinico.domain.model.AlertType
import com.example.medicaseclinico.domain.model.BatteryThresholds
import com.example.medicaseclinico.domain.model.Geofence
import com.example.medicaseclinico.domain.model.NotificationPreferences
import com.example.medicaseclinico.domain.model.TemperatureStatus
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Publica las alertas de MediCase como notificaciones del sistema,
 * aplicando antirrebote para no saturar al usuario y respetando las preferencias guardadas.
 */
class SystemAlertPublisher(
    context: Context,
    private val preferencesProvider: () -> NotificationPreferences,
    private val clock: () -> Long = System::currentTimeMillis
) : AlertPublisher {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    // --- Estado del antirrebote ---
    private var temperatureAlertActive = false
    private var lastTemperatureAlertAt = 0L
    private var lastTemperatureUpdateAt = 0L
    private var lowBatteryAlertActive = false
    private var hasBeenConnected = false
    private var disconnectionAlertActive = false

    // ------------------------------------------------------------------
    //  Temperatura
    // ------------------------------------------------------------------
    @Synchronized
    override fun onTemperature(celsius: Float, status: TemperatureStatus) {
        val now = clock()
        if (!status.isAlert) {
            if (temperatureAlertActive) {
                notificationManager.cancel(NOTIFICATION_ID_TEMPERATURE)
                temperatureAlertActive = false
            }
            return
        }

        val isNewAlert = !temperatureAlertActive
        val reminderDue = now - lastTemperatureAlertAt >= TEMPERATURE_REMINDER_MS
        val shouldSound = isNewAlert || reminderDue
        val silentUpdateDue = now - lastTemperatureUpdateAt >= TEMPERATURE_UPDATE_MS

        if (!shouldSound && !silentUpdateDue) return

        val temperatureText = appContext.getString(R.string.temperature_value, celsius)
        post(
            id = NOTIFICATION_ID_TEMPERATURE,
            channel = AlertChannel.TEMPERATURE,
            title = appContext.getString(R.string.notif_temperature_title),
            text = appContext.getString(
                R.string.notif_temperature_text,
                temperatureText,
                TemperatureStatus.MIN_SAFE_CELSIUS,
                TemperatureStatus.MAX_SAFE_CELSIUS
            ),
            silent = !shouldSound,
            ongoing = true
        )

        temperatureAlertActive = true
        lastTemperatureUpdateAt = now
        if (shouldSound) lastTemperatureAlertAt = now
    }

    // ------------------------------------------------------------------
    //  Batería (con histéresis)
    // ------------------------------------------------------------------
    @Synchronized
    override fun onBatteryLevel(percent: Int) {
        when {
            percent < BatteryThresholds.LOW_PERCENT && !lowBatteryAlertActive -> {
                post(
                    id = NOTIFICATION_ID_BATTERY,
                    channel = AlertChannel.BATTERY,
                    title = appContext.getString(R.string.notif_battery_title),
                    text = appContext.getString(R.string.notif_battery_text, percent),
                    silent = false,
                    ongoing = false
                )
                lowBatteryAlertActive = true
            }
            percent >= BatteryThresholds.REARM_PERCENT && lowBatteryAlertActive -> {
                notificationManager.cancel(NOTIFICATION_ID_BATTERY)
                lowBatteryAlertActive = false
            }
        }
    }

    // ------------------------------------------------------------------
    //  Conexión
    // ------------------------------------------------------------------
    @Synchronized
    override fun onConnectionLost() {
        if (!hasBeenConnected || disconnectionAlertActive) return
        post(
            id = NOTIFICATION_ID_CONNECTION,
            channel = AlertChannel.CONNECTION,
            title = appContext.getString(R.string.notif_connection_title),
            text = appContext.getString(R.string.notif_connection_text),
            silent = false,
            ongoing = false
        )
        disconnectionAlertActive = true
    }

    @Synchronized
    override fun onConnectionRestored() {
        hasBeenConnected = true
        if (disconnectionAlertActive) {
            notificationManager.cancel(NOTIFICATION_ID_CONNECTION)
            disconnectionAlertActive = false
        }
    }

    // ------------------------------------------------------------------
    //  Geocercas
    // ------------------------------------------------------------------
    /**
     * Una notificación por zona (etiqueta con su id): salir de "Casa" no debe
     * tapar el aviso de que también salió de "Clínica".
     */
    @Synchronized
    override fun onGeofenceExited(geofence: Geofence) {
        post(
            id = NOTIFICATION_ID_GEOFENCE,
            tag = geofenceTag(geofence),
            channel = AlertChannel.GEOFENCE,
            title = appContext.getString(R.string.notif_geofence_title),
            text = appContext.getString(R.string.notif_geofence_text, geofence.name),
            silent = false,
            ongoing = false
        )
    }

    /** El estuche volvió: el aviso de salida de esa zona ya no aplica. */
    @Synchronized
    override fun onGeofenceEntered(geofence: Geofence) {
        notificationManager.cancel(geofenceTag(geofence), NOTIFICATION_ID_GEOFENCE)
    }

    private fun geofenceTag(geofence: Geofence) = "$GEOFENCE_TAG_PREFIX${geofence.id}"

    // ------------------------------------------------------------------
    //  Construcción y envío seguro
    // ------------------------------------------------------------------
    private fun post(
        id: Int,
        channel: AlertChannel,
        title: String,
        text: String,
        silent: Boolean,
        ongoing: Boolean,
        tag: String? = null
    ) {
        if (!canPostNotifications()) return
        if (!preferencesProvider().allows(channel.toAlertType(), currentMinuteOfDay())) return

        val notification = NotificationCompat.Builder(appContext, channel.id)
            .setSmallIcon(R.drawable.ic_notification_medicase)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(ALERT_COLOR)
            .setCategory(
                if (channel.isCritical) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_STATUS
            )
            .setPriority(
                if (channel.isCritical) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setOnlyAlertOnce(silent)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(openAppIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        try {
            notificationManager.notify(tag, id, notification)
        } catch (e: SecurityException) {
            // Permiso revocado por el usuario
        }
    }

    private fun canPostNotifications(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && notificationManager.areNotificationsEnabled()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Hora local del teléfono en minutos desde la medianoche. */
    private fun currentMinuteOfDay(): Int {
        val time = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalTime()
        return time.hour * 60 + time.minute
    }

    private fun AlertChannel.toAlertType(): AlertType = when (this) {
        AlertChannel.TEMPERATURE -> AlertType.TEMPERATURE
        AlertChannel.BATTERY -> AlertType.BATTERY
        AlertChannel.CONNECTION -> AlertType.CONNECTION
        AlertChannel.GEOFENCE -> AlertType.GEOFENCE
        AlertChannel.SECURITY -> AlertType.SECURITY
    }

    private companion object {
        const val NOTIFICATION_ID_TEMPERATURE = 1001
        const val NOTIFICATION_ID_BATTERY = 1002
        const val NOTIFICATION_ID_CONNECTION = 1003
        const val NOTIFICATION_ID_GEOFENCE = 1004
        const val GEOFENCE_TAG_PREFIX = "geofence-"
        val TEMPERATURE_REMINDER_MS = TimeUnit.MINUTES.toMillis(15)
        val TEMPERATURE_UPDATE_MS = TimeUnit.SECONDS.toMillis(30)
        const val ALERT_COLOR = 0xFFD64535.toInt()
    }
}