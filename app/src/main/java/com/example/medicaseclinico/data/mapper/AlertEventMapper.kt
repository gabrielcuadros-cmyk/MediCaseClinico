package com.example.medicaseclinico.data.mapper

import com.example.medicaseclinico.data.local.entity.AlertEventEntity
import com.example.medicaseclinico.domain.model.AlertEvent
import com.example.medicaseclinico.domain.model.AlertType

/**
 * Conversión entre la fila de Room y la alerta del dominio.
 *
 * @return null si el tipo guardado no existe en esta versión de la app (por
 * ejemplo, tras restaurar datos de una versión más nueva): la fila se omite
 * en lugar de provocar un cierre al leer la lista.
 */
fun AlertEventEntity.toDomain(): AlertEvent? {
    val alertType = AlertType.entries.firstOrNull { it.name == type } ?: return null
    return AlertEvent(
        id = id,
        type = alertType,
        timestamp = timestamp,
        value = numericValue,
        isRead = isRead
    )
}

fun AlertEvent.toEntity(): AlertEventEntity = AlertEventEntity(
    id = id,
    type = type.name,
    timestamp = timestamp,
    numericValue = value,
    isRead = isRead
)
