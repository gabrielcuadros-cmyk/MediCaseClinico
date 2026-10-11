package com.example.medicaseclinico.data.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Permiso POST_NOTIFICATIONS: solo existe en Android 13+ (API 33).
 * En versiones anteriores las notificaciones están permitidas por defecto.
 */
object NotificationPermission {
    const val PERMISSION = Manifest.permission.POST_NOTIFICATIONS

    /** true si este sistema exige pedir el permiso en tiempo de ejecución. */
    val isRuntimePermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isGranted(context: Context): Boolean =
        !isRuntimePermission ||
                ContextCompat.checkSelfPermission(context, PERMISSION) ==
                PackageManager.PERMISSION_GRANTED

    /** true si hay que mostrar el diálogo del sistema. */
    fun shouldRequest(context: Context): Boolean = isRuntimePermission && !isGranted(context)
}