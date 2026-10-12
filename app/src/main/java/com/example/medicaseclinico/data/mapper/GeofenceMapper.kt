package com.example.medicaseclinico.data.mapper

import com.example.medicaseclinico.data.local.entity.GeofenceEntity
import com.example.medicaseclinico.domain.model.Geofence

/** Conversión entre la fila de Room y la zona del dominio. */
fun GeofenceEntity.toDomain(): Geofence = Geofence(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    isActive = isActive
)

fun Geofence.toEntity(): GeofenceEntity = GeofenceEntity(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    isActive = isActive
)
