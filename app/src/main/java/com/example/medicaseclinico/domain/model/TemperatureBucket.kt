package com.example.medicaseclinico.domain.model

/**
 * Resumen de todas las lecturas de un intervalo de tiempo del historial.
 *
 * El rango de 30 días puede superar las 250 000 lecturas; cargarlas una a una
 * congelaría la pantalla. Cada intervalo conserva el mínimo y el máximo, así
 * que ningún pico fuera de 2–8 °C se pierde aunque dure una sola lectura.
 *
 * @param startTimestamp inicio del intervalo (epoch ms).
 * @param averageCelsius media de las lecturas del intervalo → línea de la gráfica.
 * @param outOfRangeCount cuántas de las [sampleCount] lecturas estuvieron fuera
 * del rango seguro → porcentaje de tiempo en rango.
 * @param lastBatteryLevel batería de la lectura más reciente que la reportó,
 * o null si ninguna lectura del intervalo traía batería.
 */
data class TemperatureBucket(
    val startTimestamp: Long,
    val averageCelsius: Float,
    val minCelsius: Float,
    val maxCelsius: Float,
    val sampleCount: Int,
    val outOfRangeCount: Int,
    val lastBatteryLevel: Int? = null
)
