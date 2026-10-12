package com.example.medicaseclinico.domain.chart

import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.usecase.CalculateTemperatureStatsUseCase

/**
 * Geometría de la gráfica de estabilidad, independiente de píxeles y de
 * Compose. Vive en el dominio porque la comparten la pantalla y el Reporte
 * Médico (PDF): ambos deben dibujar exactamente la misma gráfica. Además
 * permite probarla con tests JVM.
 *
 * @param minCelsius temperatura del borde inferior de la gráfica.
 * @param maxCelsius temperatura del borde superior.
 * @param segments tramos continuos de la línea; se parte solo donde el hueco
 * sin lecturas es largo, para no dibujar una línea sobre una desconexión.
 * @param excursions intervalos con algún valor fuera del rango seguro.
 */
data class StabilityChartModel(
    val minCelsius: Float,
    val maxCelsius: Float,
    val segments: List<List<ChartPoint>>,
    val excursions: List<ChartExcursion>
) {
    val pointCount: Int
        get() = segments.sumOf { it.size }

    /** Posición vertical de [celsius]: 0 = borde superior, 1 = borde inferior. */
    fun yFraction(celsius: Float): Float =
        (maxCelsius - celsius) / (maxCelsius - minCelsius)
}

/** Punto de la línea: [xFraction] va de 0 (inicio del rango) a 1 (ahora). */
data class ChartPoint(val xFraction: Float, val celsius: Float)

/** Extremos reales de un intervalo que salió del rango seguro. */
data class ChartExcursion(
    val xFraction: Float,
    val minCelsius: Float,
    val maxCelsius: Float
)

/** Margen fijo alrededor de la banda segura, para que quepan sus etiquetas. */
private const val BAND_MARGIN_CELSIUS = 2f

/** Aire entre un pico y el borde de la gráfica. */
private const val PEAK_MARGIN_CELSIUS = 0.5f

/**
 * La escala vertical siempre incluye la banda 2–8 °C con margen y se amplía
 * solo si hay picos, de modo que una insulina estable se ve igual que en el
 * diseño y un pico nunca queda cortado.
 *
 * @param maxGapMs tiempo máximo entre dos intervalos con lecturas para seguir
 * uniéndolos con la línea. Es el mismo tope que usan las estadísticas para
 * decidir cuánto tiempo "cubre" una lectura: así la gráfica y el "tiempo
 * vigilado" coinciden en qué es una pausa breve y qué es una desconexión. Si
 * el intervalo es más largo que este tope (rangos de 7 y 30 días), dos
 * intervalos vecinos siguen contando como seguidos.
 */
fun buildStabilityChartModel(
    buckets: List<TemperatureBucket>,
    bucketMs: Long,
    rangeStart: Long,
    rangeEnd: Long,
    maxGapMs: Long = CalculateTemperatureStatsUseCase.DEFAULT_MAX_SAMPLE_GAP_MS
): StabilityChartModel {
    val sorted = buckets.sortedBy { it.startTimestamp }
    val spanMs = (rangeEnd - rangeStart).coerceAtLeast(1L).toDouble()
    val maxContinuousStepMs = maxOf(bucketMs, maxGapMs)

    fun xFractionOf(bucket: TemperatureBucket): Float {
        val center = (bucket.startTimestamp + bucketMs / 2).coerceAtMost(rangeEnd)
        return ((center - rangeStart) / spanMs).coerceIn(0.0, 1.0).toFloat()
    }

    val segments = mutableListOf<MutableList<ChartPoint>>()
    var previousStart: Long? = null
    for (bucket in sorted) {
        val isContinuous = previousStart != null &&
            bucket.startTimestamp - previousStart <= maxContinuousStepMs
        if (!isContinuous) segments += mutableListOf<ChartPoint>()
        segments.last() += ChartPoint(xFractionOf(bucket), bucket.averageCelsius)
        previousStart = bucket.startTimestamp
    }

    val excursions = sorted
        .filter {
            it.maxCelsius > TemperatureStatus.MAX_SAFE_CELSIUS ||
                it.minCelsius < TemperatureStatus.MIN_SAFE_CELSIUS
        }
        .map { ChartExcursion(xFractionOf(it), it.minCelsius, it.maxCelsius) }

    val lowestCelsius = sorted.minOfOrNull { it.minCelsius }
    val highestCelsius = sorted.maxOfOrNull { it.maxCelsius }

    return StabilityChartModel(
        minCelsius = minOf(
            TemperatureStatus.MIN_SAFE_CELSIUS - BAND_MARGIN_CELSIUS,
            (lowestCelsius ?: TemperatureStatus.MIN_SAFE_CELSIUS) - PEAK_MARGIN_CELSIUS
        ),
        maxCelsius = maxOf(
            TemperatureStatus.MAX_SAFE_CELSIUS + BAND_MARGIN_CELSIUS,
            (highestCelsius ?: TemperatureStatus.MAX_SAFE_CELSIUS) + PEAK_MARGIN_CELSIUS
        ),
        segments = segments,
        excursions = excursions
    )
}
