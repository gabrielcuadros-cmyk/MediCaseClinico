package com.example.medicaseclinico.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lectura térmica registrada por el estuche MediCase vía BLE.
 *
 * Cada fila es una muestra real del sensor: alimenta la tarjeta de 4.2 °C del
 * Dashboard, la gráfica de estabilidad (2–8 °C) y la línea de eventos del
 * Historial Térmico (filtros 24h / 7 días / 30 días).
 *
 * El índice sobre [timestamp] acelera las consultas por rango de fechas y el
 * ordenamiento "más reciente primero".
 */
@Entity(
    tableName = "temperature_readings",
    indices = [Index(value = ["timestamp"])]
)
data class TemperatureEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** Temperatura interna del estuche en grados Celsius (ej. 4.2f). */
    @ColumnInfo(name = "temperature_celsius")
    val temperatureCelsius: Float,

    /** Marca de tiempo de la lectura en milisegundos epoch (System.currentTimeMillis()). */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    /**
     * true si la lectura salió del rango seguro de la insulina (2 °C – 8 °C)
     * y disparó una alerta. Lo calcula el ViewModel, no la UI.
     */
    @ColumnInfo(name = "is_alert_triggered")
    val isAlertTriggered: Boolean,

    /**
     * Nivel de batería del estuche (0–100) reportado en la misma trama BLE.
     * Nullable: si el firmware no envía batería en una muestra, no se inventa un valor.
     */
    @ColumnInfo(name = "battery_level")
    val batteryLevel: Int? = null
)