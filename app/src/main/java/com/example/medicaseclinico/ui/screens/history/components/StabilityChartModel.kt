package com.example.medicaseclinico.ui.screens.history.components

import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureStatus

/**
 * Geometría de la gráfica de estabilidad, independiente de píxeles y de
 * Compose para poder probarla con tests JVM.
 *
 * @param minCelsius temperatura del borde inferior de la gráfica.
 * @param maxCelsius temperatura del borde superior.
 * @param segments tramos continuos de la línea; se parte donde faltan
 * intervalos para no dibujar una línea sobre tiempo sin lecturas.
 * @param excursions intervalos con algún valor fuera del rango seguro.
 */
internal data class StabilityChartModel(
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
internal data class ChartPoint(val xFraction: Float, val celsius: Float)

/** Extremos reales de un intervalo que salió del rango seguro. */
internal data class ChartExcursion(
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
 */
internal fun buildStabilityChartModel(
    buckets: List<TemperatureBucket>,
    bucketMs: Long,
    rangeStart: Long,
    rangeEnd: Long
): StabilityChartModel {
    val sorted = buckets.sortedBy { it.startTimestamp }
    val spanMs = (rangeEnd - rangeStart).coerceAtLeast(1L).toDouble()

    fun xFractionOf(bucket: TemperatureBucket): Float {
        val center = (bucket.startTimestamp + bucketMs / 2).coerceAtMost(rangeEnd)
        return ((center - rangeStart) / spanMs).coerceIn(0.0, 1.0).toFloat()
    }

    val segments = mutableListOf<MutableList<ChartPoint>>()
    var previousStart: Long? = null
    for (bucket in sorted) {
        val isContinuous = previousStart != null && bucket.startTimestamp - previousStart <= bucketMs
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
