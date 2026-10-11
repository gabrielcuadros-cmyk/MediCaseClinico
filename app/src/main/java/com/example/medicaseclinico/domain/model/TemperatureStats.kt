package com.example.medicaseclinico.domain.model

/**
 * Resumen estadístico de la temperatura de la insulina en un rango.
 * Alimenta la pantalla de Historial y el Reporte Médico (PDF).
 */
data class TemperatureStats(
    val sampleCount: Int,
    val minCelsius: Float,
    val maxCelsius: Float,
    val averageCelsius: Float,
    val timeInSafeRangePercent: Float,
    val monitoredDurationMs: Long
)