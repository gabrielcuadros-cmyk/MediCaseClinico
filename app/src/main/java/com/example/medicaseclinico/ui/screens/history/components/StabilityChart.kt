package com.example.medicaseclinico.ui.screens.history.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.chart.StabilityChartModel
import com.example.medicaseclinico.domain.chart.buildStabilityChartModel
import com.example.medicaseclinico.domain.model.HistoryRange
import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureStats
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.time.DateTimeLabels
import com.example.medicaseclinico.ui.theme.MediCaseTheme

private val ChartHeight = 150.dp
private val LineWidth = 2.dp
private val GuideWidth = 1.dp
private val GuideDash = 4.dp
private val GuideGap = 3.dp
private val PointRadius = 2.5.dp
private val ExcursionRadius = 3.dp
private val LabelInset = 4.dp
private val LabelGap = 2.dp

/** Con más puntos que estos, marcar cada uno taparía la línea. */
private const val MAX_POINTS_WITH_DOTS = 12

/** Marcas del eje de tiempo: inicio, tres intermedias y "Ahora". */
private const val AXIS_LABEL_COUNT = 5

/**
 * Tarjeta "Gráfica de estabilidad (2-8 °C)" del Historial Térmico.
 *
 * Sin lógica de negocio: recibe los intervalos y las estadísticas ya
 * calculados por el ViewModel y solo los dibuja. Si no hay lecturas muestra
 * la banda segura vacía con un aviso, nunca una línea inventada.
 *
 * @param buckets lecturas del rango resumidas por intervalos, en orden cronológico.
 * @param stats resumen del rango; se usa para describir la gráfica a TalkBack.
 * @param rangeStart inicio de la ventana mostrada (epoch ms).
 * @param rangeEnd fin de la ventana mostrada (epoch ms), el "Ahora" del eje.
 */
@Composable
fun StabilityChartCard(
    buckets: List<TemperatureBucket>,
    stats: TemperatureStats?,
    range: HistoryRange,
    rangeStart: Long,
    rangeEnd: Long,
    modifier: Modifier = Modifier
) {
    val model = remember(buckets, range, rangeStart, rangeEnd) {
        buildStabilityChartModel(
            buckets = buckets,
            bucketMs = range.bucketMs,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd
        )
    }
    val hasData = stats != null && model.pointCount > 0
    val emptyText = stringResource(R.string.history_chart_empty)
    val description = if (stats != null && hasData) {
        stringResource(
            R.string.history_chart_description,
            stringResource(R.string.temperature_value, stats.minCelsius),
            stringResource(R.string.temperature_value, stats.maxCelsius),
            stringResource(R.string.temperature_value, stats.averageCelsius),
            stats.timeInSafeRangePercent
        )
    } else {
        emptyText
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    R.string.history_chart_title,
                    TemperatureStatus.MIN_SAFE_CELSIUS,
                    TemperatureStatus.MAX_SAFE_CELSIUS
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChartHeight),
                contentAlignment = Alignment.Center
            ) {
                StabilityChartCanvas(
                    model = model,
                    description = description,
                    modifier = Modifier.fillMaxSize()
                )
                if (!hasData) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TimeAxisLabels(range = range, rangeStart = rangeStart, rangeEnd = rangeEnd)
        }
    }
}

/**
 * Dibuja la banda segura, sus dos guías punteadas con etiqueta, la línea de
 * temperatura media y, en rojo, el recorrido real de los intervalos que
 * salieron del rango. El pico se reconoce por su posición fuera de la banda,
 * no solo por el color.
 */
@Composable
private fun StabilityChartCanvas(
    model: StabilityChartModel,
    description: String,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val statusColors = MediCaseTheme.statusColors
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = statusColors.safe)
    val textMeasurer = rememberTextMeasurer()
    val maxLabel = stringResource(R.string.history_chart_max_safe, TemperatureStatus.MAX_SAFE_CELSIUS)
    val minLabel = stringResource(R.string.history_chart_min_safe, TemperatureStatus.MIN_SAFE_CELSIUS)
    val maxLabelLayout = remember(textMeasurer, maxLabel, labelStyle) {
        textMeasurer.measure(maxLabel, labelStyle)
    }
    val minLabelLayout = remember(textMeasurer, minLabel, labelStyle) {
        textMeasurer.measure(minLabel, labelStyle)
    }

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        fun yOf(celsius: Float) = size.height * model.yFraction(celsius)
        fun xOf(fraction: Float) = size.width * fraction

        val maxSafeY = yOf(TemperatureStatus.MAX_SAFE_CELSIUS)
        val minSafeY = yOf(TemperatureStatus.MIN_SAFE_CELSIUS)

        // Banda segura y sus guías punteadas.
        drawRect(
            color = statusColors.safeContainer,
            topLeft = Offset(0f, maxSafeY),
            size = Size(size.width, minSafeY - maxSafeY)
        )
        val dash = PathEffect.dashPathEffect(floatArrayOf(GuideDash.toPx(), GuideGap.toPx()))
        for (guideY in listOf(maxSafeY, minSafeY)) {
            drawLine(
                color = statusColors.safe,
                start = Offset(0f, guideY),
                end = Offset(size.width, guideY),
                strokeWidth = GuideWidth.toPx(),
                pathEffect = dash
            )
        }
        drawText(
            textLayoutResult = maxLabelLayout,
            topLeft = Offset(
                x = LabelInset.toPx(),
                y = maxSafeY - maxLabelLayout.size.height - LabelGap.toPx()
            )
        )
        drawText(
            textLayoutResult = minLabelLayout,
            topLeft = Offset(x = LabelInset.toPx(), y = minSafeY + LabelGap.toPx())
        )

        // Línea de temperatura media, partida donde faltan lecturas.
        val showDots = model.pointCount <= MAX_POINTS_WITH_DOTS
        for (segment in model.segments) {
            if (segment.size > 1) {
                val path = Path()
                segment.forEachIndexed { index, point ->
                    val x = xOf(point.xFraction)
                    val y = yOf(point.celsius)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = LineWidth.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            if (showDots || segment.size == 1) {
                for (point in segment) {
                    drawCircle(
                        color = lineColor,
                        radius = PointRadius.toPx(),
                        center = Offset(xOf(point.xFraction), yOf(point.celsius))
                    )
                }
            }
        }

        // Recorrido real (mínimo–máximo) de los intervalos fuera de rango.
        for (excursion in model.excursions) {
            val x = xOf(excursion.xFraction)
            drawLine(
                color = statusColors.critical,
                start = Offset(x, yOf(excursion.maxCelsius)),
                end = Offset(x, yOf(excursion.minCelsius)),
                strokeWidth = LineWidth.toPx(),
                cap = StrokeCap.Round
            )
            if (excursion.maxCelsius > TemperatureStatus.MAX_SAFE_CELSIUS) {
                drawCircle(
                    color = statusColors.critical,
                    radius = ExcursionRadius.toPx(),
                    center = Offset(x, yOf(excursion.maxCelsius))
                )
            }
            if (excursion.minCelsius < TemperatureStatus.MIN_SAFE_CELSIUS) {
                drawCircle(
                    color = statusColors.critical,
                    radius = ExcursionRadius.toPx(),
                    center = Offset(x, yOf(excursion.minCelsius))
                )
            }
        }
    }
}

/** Eje de tiempo: horas en el rango de 24 h, fechas en los rangos de días. */
@Composable
private fun TimeAxisLabels(
    range: HistoryRange,
    rangeStart: Long,
    rangeEnd: Long,
    modifier: Modifier = Modifier
) {
    val dateTimeLabels = remember { DateTimeLabels.system() }
    val nowLabel = stringResource(R.string.history_chart_now)
    val labels = remember(dateTimeLabels, range, rangeStart, rangeEnd, nowLabel) {
        // La última marca es el presente: se dice "Ahora" en lugar de su hora.
        dateTimeLabels.axisLabels(range, rangeStart, rangeEnd, count = AXIS_LABEL_COUNT)
            .dropLast(1) + nowLabel
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        for (label in labels) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ------------------------------------------------------------------
//  Previews (valores de ejemplo solo para el IDE)
// ------------------------------------------------------------------
private const val PREVIEW_RANGE_END = 1_760_000_000_000L
private val PreviewRange = HistoryRange.LAST_24_HOURS
private val PreviewRangeStart = PreviewRange.startAt(PREVIEW_RANGE_END)

private fun previewBuckets(peakAtIndex: Int? = null): List<TemperatureBucket> {
    val averages = listOf(4.3f, 4.1f, 4.2f, 3.9f, 4.0f, 4.5f, 4.7f, 4.1f, 4.0f, 4.2f)
    val stepMs = PreviewRange.durationMs / averages.size
    return averages.mapIndexed { index, average ->
        val isPeak = index == peakAtIndex
        TemperatureBucket(
            startTimestamp = PreviewRangeStart + index * stepMs,
            averageCelsius = if (isPeak) 7.6f else average,
            minCelsius = average - 0.2f,
            maxCelsius = if (isPeak) 9.4f else average + 0.2f,
            sampleCount = 12,
            outOfRangeCount = if (isPeak) 4 else 0
        )
    }
}

private fun previewStats(buckets: List<TemperatureBucket>) = TemperatureStats(
    sampleCount = buckets.sumOf { it.sampleCount },
    minCelsius = buckets.minOf { it.minCelsius },
    maxCelsius = buckets.maxOf { it.maxCelsius },
    averageCelsius = buckets.map { it.averageCelsius }.average().toFloat(),
    timeInSafeRangePercent = 97f,
    monitoredDurationMs = PreviewRange.durationMs
)

@Preview(showBackground = true, name = "Gráfica - Estable")
@Composable
private fun StabilityChartStablePreview() {
    val buckets = previewBuckets()
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            StabilityChartCard(
                buckets = buckets,
                stats = previewStats(buckets),
                range = PreviewRange,
                rangeStart = PreviewRangeStart,
                rangeEnd = PREVIEW_RANGE_END,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "Gráfica - Pico fuera de rango")
@Composable
private fun StabilityChartPeakPreview() {
    val buckets = previewBuckets(peakAtIndex = 6)
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            StabilityChartCard(
                buckets = buckets,
                stats = previewStats(buckets),
                range = PreviewRange,
                rangeStart = PreviewRangeStart,
                rangeEnd = PREVIEW_RANGE_END,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "Gráfica - Sin datos")
@Composable
private fun StabilityChartEmptyPreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            StabilityChartCard(
                buckets = emptyList(),
                stats = null,
                range = PreviewRange,
                rangeStart = PreviewRangeStart,
                rangeEnd = PREVIEW_RANGE_END,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
