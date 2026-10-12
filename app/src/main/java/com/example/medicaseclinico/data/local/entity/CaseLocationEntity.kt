package com.example.medicaseclinico.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Posición medida por el GPS del estuche y recibida por BLE.
 *
 * Solo se guardan posiciones reales: una trama "sin señal" no genera fila.
 * Así la fila más reciente es siempre la última ubicación conocida, que el
 * mapa muestra con su hora cuando el estuche está fuera del alcance BLE.
 *
 * El índice sobre [timestamp] acelera la consulta de la más reciente y la
 * limpieza de las antiguas.
 */
@Entity(
    tableName = "case_locations",
    indices = [Index(value = ["timestamp"])]
)
data class CaseLocationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** Grados decimales, de -90 (sur) a 90 (norte). */
    @ColumnInfo(name = "latitude")
    val latitude: Double,

    /** Grados decimales, de -180 (oeste) a 180 (este). */
    @ColumnInfo(name = "longitude")
    val longitude: Double,

    /** Momento en que el teléfono recibió la posición, en milisegundos epoch. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)
