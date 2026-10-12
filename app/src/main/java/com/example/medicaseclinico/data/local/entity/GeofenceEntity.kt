package com.example.medicaseclinico.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Zona de seguridad configurada por el usuario ("Casa", "Clínica…").
 *
 * Es un círculo: centro y radio. Si el estuche está dentro o fuera no se
 * guarda aquí; se calcula con la posición actual de su GPS.
 */
@Entity(tableName = "geofences")
data class GeofenceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** Nombre que el usuario dio a la zona. */
    @ColumnInfo(name = "name")
    val name: String,

    /** Centro de la zona, grados decimales. */
    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    /** Radio de la zona en metros. */
    @ColumnInfo(name = "radius_meters")
    val radiusMeters: Double,

    /** false si el usuario la conserva pero no quiere alertas de ella. */
    @ColumnInfo(name = "is_active")
    val isActive: Boolean
)
