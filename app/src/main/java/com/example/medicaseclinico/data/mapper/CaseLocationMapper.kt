package com.example.medicaseclinico.data.mapper

import com.example.medicaseclinico.data.local.entity.CaseLocationEntity
import com.example.medicaseclinico.domain.model.CaseLocation

/** Conversión entre la fila de Room y la posición del dominio. */
fun CaseLocationEntity.toDomain(): CaseLocation = CaseLocation(
    id = id,
    latitude = latitude,
    longitude = longitude,
    timestamp = timestamp
)

fun CaseLocation.toEntity(): CaseLocationEntity = CaseLocationEntity(
    id = id,
    latitude = latitude,
    longitude = longitude,
    timestamp = timestamp
)
