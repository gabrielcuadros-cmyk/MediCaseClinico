package com.example.medicaseclinico.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Alerta emitida por la app (temperatura, batería, conexión, geocerca o
 * seguridad), guardada para la pantalla "Alertas de Seguridad".
 *
 * El índice sobre [timestamp] acelera el orden "más reciente primero" de la
 * lista.
 */
@Entity(
    tableName = "alert_events",
    indices = [Index(value = ["timestamp"])]
)
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /**
     * Nombre del AlertType del dominio. Se guarda como texto y no como número
     * para que reordenar o añadir tipos al enum no cambie el significado de
     * las filas ya guardadas.
     */
    @ColumnInfo(name = "type")
    val type: String,

    /** Momento en que se emitió la alerta, en milisegundos epoch. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    /** Temperatura (°C) o batería (%) que disparó la alerta; null si no aplica. */
    @ColumnInfo(name = "numeric_value")
    val numericValue: Float? = null,

    /** false hasta que el usuario abre la lista de alertas. */
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,

    /**
     * Dato de texto de la alerta; hoy, el nombre de la zona en una alerta de
     * geocerca. null si el tipo no lo necesita.
     *
     * Debe seguir siendo el último campo: la migración 3 → 4 lo añade con
     * ALTER TABLE, que siempre agrega la columna al final.
     */
    @ColumnInfo(name = "detail")
    val detail: String? = null
)
