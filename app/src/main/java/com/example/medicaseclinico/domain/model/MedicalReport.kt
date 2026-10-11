package com.example.medicaseclinico.domain.model

/**
 * Contenido del Reporte Médico de un rango del historial térmico.
 *
 * Reúne exactamente lo que el usuario tiene en pantalla al exportar, para
 * que el documento que recibe el médico no difiera de lo que vio el paciente.
 *
 * @param generatedAt momento en que se generó el reporte (epoch ms).
 * @param currentCelsius última temperatura registrada por el estuche.
 * @param currentReadingAt momento de esa última lectura (epoch ms).
 * @param buckets lecturas del rango resumidas por intervalos → gráfica.
 * @param events línea de eventos del rango, del más reciente al más antiguo.
 */
data class MedicalReport(
    val range: HistoryRange,
    val rangeStart: Long,
    val rangeEnd: Long,
    val generatedAt: Long,
    val currentCelsius: Float,
    val currentReadingAt: Long,
    val stats: TemperatureStats,
    val buckets: List<TemperatureBucket>,
    val events: List<HistoryEvent>
)
