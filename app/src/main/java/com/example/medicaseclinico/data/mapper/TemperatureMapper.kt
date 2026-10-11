package com.example.medicaseclinico.data.mapper

import com.example.medicaseclinico.data.local.entity.TemperatureBucketRow
import com.example.medicaseclinico.data.local.entity.TemperatureEntity
import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureReading

/**
 * Conversión entre el modelo de Room (capa data) y el modelo de negocio (capa domain).
 * Mantiene a Room como un detalle de implementación.
 */
fun TemperatureEntity.toDomain(): TemperatureReading = TemperatureReading(
    id = id,
    temperatureCelsius = temperatureCelsius,
    timestamp = timestamp,
    isAlertTriggered = isAlertTriggered,
    batteryLevel = batteryLevel
)

fun TemperatureReading.toEntity(): TemperatureEntity = TemperatureEntity(
    id = id,
    temperatureCelsius = temperatureCelsius,
    timestamp = timestamp,
    isAlertTriggered = isAlertTriggered,
    batteryLevel = batteryLevel
)

fun List<TemperatureEntity>.toDomain(): List<TemperatureReading> = map { it.toDomain() }

fun TemperatureBucketRow.toDomain(): TemperatureBucket = TemperatureBucket(
    startTimestamp = bucketStart,
    averageCelsius = averageCelsius,
    minCelsius = minCelsius,
    maxCelsius = maxCelsius,
    sampleCount = sampleCount,
    outOfRangeCount = outOfRangeCount,
    lastBatteryLevel = lastBatteryLevel
)
