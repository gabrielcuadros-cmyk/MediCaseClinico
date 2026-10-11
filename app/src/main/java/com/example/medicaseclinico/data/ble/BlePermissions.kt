package com.example.medicaseclinico.data.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Punto único de verdad sobre qué necesita la app para hablar con el estuche.
 *
 * Android cambió el modelo de permisos BLE en la API 31: esta clase oculta
 * esa diferencia para que ni el ViewModel ni la UI tengan que comprobar
 * versiones del sistema.
 */
object BlePermissions {

    /**
     * Permisos que deben concederse en tiempo de ejecución según la versión:
     *  - Android 12+ → "Dispositivos cercanos" (SCAN + CONNECT)
     *  - Android 8–11 → ubicación precisa (requisito del sistema para escanear BLE)
     */
    val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** true si todos los permisos de [requiredPermissions] están concedidos. */
    fun hasAllPermissions(context: Context): Boolean =
        requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }

    /**
     * true si el teléfono tiene hardware BLE. En emuladores suele ser false:
     * la app debe mostrar un error controlado, nunca intentar escanear.
     */
    fun isBleSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    /**
     * true si el Bluetooth está encendido. Envuelto en try-catch porque en
     * algunos fabricantes el adaptador puede lanzar SecurityException si
     * faltan permisos, y eso no debe cerrar la app.
     */
    fun isBluetoothEnabled(context: Context): Boolean = try {
        val manager = context.getSystemService(BluetoothManager::class.java)
        manager?.adapter?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }
}