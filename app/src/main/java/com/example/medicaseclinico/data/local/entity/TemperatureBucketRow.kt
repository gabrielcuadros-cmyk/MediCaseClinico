package com.example.medicaseclinico.data.local.entity

import androidx.room.ColumnInfo

/**
 * Fila del resultado de la consulta agregada por intervalos de
 * "temperature_readings". No es una tabla: solo da forma al resultado para
 * que Room lo lea; el dominio usa TemperatureBucket.
 */
data class TemperatureBucketRow(
    @ColumnInfo(name = "bucketStart")
    val bucketStart: Long,

    @ColumnInfo(name = "averageCelsius")
    val averageCelsius: Float,

    @ColumnInfo(name = "minCelsius")
    val minCelsius: Float,

    @ColumnInfo(name = "maxCelsius")
    val maxCelsius: Float,

    @ColumnInfo(name = "sampleCount")
    val sampleCount: Int,

    @ColumnInfo(name = "outOfRangeCount")
    val outOfRangeCount: Int,

    @ColumnInfo(name = "lastBatteryLevel")
    val lastBatteryLevel: Int?
)
