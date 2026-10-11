package com.example.medicaseclinico.data.report

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.medicaseclinico.domain.chart.StabilityChartModel
import com.example.medicaseclinico.domain.model.TemperatureStatus

/**
 * Dibuja la gráfica de estabilidad en el lienzo de una página PDF.
 *
 * Usa el mismo [StabilityChartModel] que la pantalla, así que el médico ve
 * en el reporte la misma banda 2–8 °C, la misma línea y los mismos picos que
 * el paciente vio en la app. Los colores son fijos (tema claro) porque un PDF
 * se imprime sobre blanco.
 *
 * @param maxSafeLabel texto de la guía superior ("Máx Seguro 8°C").
 * @param minSafeLabel texto de la guía inferior ("Mín Seguro 2°C").
 */
internal class PdfStabilityChartDrawer(
    private val maxSafeLabel: String,
    private val minSafeLabel: String
) {
    private val bandPaint = Paint().apply {
        color = BAND_COLOR
        style = Paint.Style.FILL
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SAFE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = GUIDE_WIDTH
        pathEffect = DashPathEffect(floatArrayOf(GUIDE_DASH, GUIDE_GAP), 0f)
    }
    private val guideLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SAFE_COLOR
        textSize = LABEL_TEXT_SIZE
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LINE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = LINE_WIDTH
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LINE_COLOR
        style = Paint.Style.FILL
    }
    private val excursionLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CRITICAL_COLOR
        style = Paint.Style.STROKE
        strokeWidth = LINE_WIDTH
        strokeCap = Paint.Cap.ROUND
    }
    private val excursionPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CRITICAL_COLOR
        style = Paint.Style.FILL
    }
    private val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AXIS_COLOR
        textSize = LABEL_TEXT_SIZE
    }

    /**
     * @param area rectángulo de la gráfica en puntos PDF, sin contar el eje.
     * @param axisLabels marcas de tiempo repartidas a lo ancho bajo la gráfica.
     * @return coordenada Y donde termina lo dibujado (gráfica más eje).
     */
    fun draw(
        canvas: Canvas,
        area: RectF,
        model: StabilityChartModel,
        axisLabels: List<String>
    ): Float {
        fun yOf(celsius: Float) = area.top + area.height() * model.yFraction(celsius)
        fun xOf(fraction: Float) = area.left + area.width() * fraction

        val maxSafeY = yOf(TemperatureStatus.MAX_SAFE_CELSIUS)
        val minSafeY = yOf(TemperatureStatus.MIN_SAFE_CELSIUS)

        // Banda segura, guías punteadas y sus etiquetas.
        canvas.drawRect(area.left, maxSafeY, area.right, minSafeY, bandPaint)
        for (guideY in listOf(maxSafeY, minSafeY)) {
            val guide = Path().apply {
                moveTo(area.left, guideY)
                lineTo(area.right, guideY)
            }
            canvas.drawPath(guide, guidePaint)
        }
        canvas.drawText(
            maxSafeLabel,
            area.left + LABEL_INSET,
            maxSafeY - LABEL_GAP - guideLabelPaint.descent(),
            guideLabelPaint
        )
        canvas.drawText(
            minSafeLabel,
            area.left + LABEL_INSET,
            minSafeY + LABEL_GAP - guideLabelPaint.ascent(),
            guideLabelPaint
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
                canvas.drawPath(path, linePaint)
            }
            if (showDots || segment.size == 1) {
                for (point in segment) {
                    canvas.drawCircle(xOf(point.xFraction), yOf(point.celsius), POINT_RADIUS, pointPaint)
                }
            }
        }

        // Recorrido real (mínimo–máximo) de los intervalos fuera de rango.
        for (excursion in model.excursions) {
            val x = xOf(excursion.xFraction)
            canvas.drawLine(
                x,
                yOf(excursion.maxCelsius),
                x,
                yOf(excursion.minCelsius),
                excursionLinePaint
            )
            if (excursion.maxCelsius > TemperatureStatus.MAX_SAFE_CELSIUS) {
                canvas.drawCircle(x, yOf(excursion.maxCelsius), EXCURSION_RADIUS, excursionPointPaint)
            }
            if (excursion.minCelsius < TemperatureStatus.MIN_SAFE_CELSIUS) {
                canvas.drawCircle(x, yOf(excursion.minCelsius), EXCURSION_RADIUS, excursionPointPaint)
            }
        }

        return drawAxis(canvas, area, axisLabels)
    }

    /** Primera marca alineada a la izquierda, última a la derecha, resto centradas. */
    private fun drawAxis(canvas: Canvas, area: RectF, labels: List<String>): Float {
        val baseline = area.bottom + AXIS_GAP - axisLabelPaint.ascent()
        val lastIndex = labels.lastIndex
        labels.forEachIndexed { index, label ->
            val labelWidth = axisLabelPaint.measureText(label)
            val anchorX = if (lastIndex > 0) {
                area.left + area.width() * index / lastIndex
            } else {
                area.left
            }
            val x = when (index) {
                0 -> anchorX
                lastIndex -> anchorX - labelWidth
                else -> anchorX - labelWidth / 2
            }
            canvas.drawText(label, x, baseline, axisLabelPaint)
        }
        return baseline + axisLabelPaint.descent()
    }

    private companion object {
        // Mismos tonos que la paleta clara de la app (ui/theme/Color.kt).
        const val BAND_COLOR = 0xFFE5F5EC.toInt()
        const val SAFE_COLOR = 0xFF1E9E5A.toInt()
        const val LINE_COLOR = 0xFF3B7BEA.toInt()
        const val CRITICAL_COLOR = 0xFFD64535.toInt()
        const val AXIS_COLOR = 0xFF6B7280.toInt()

        // Medidas en puntos PDF (1/72 de pulgada).
        const val LINE_WIDTH = 1.5f
        const val GUIDE_WIDTH = 0.75f
        const val GUIDE_DASH = 3f
        const val GUIDE_GAP = 2.5f
        const val POINT_RADIUS = 2f
        const val EXCURSION_RADIUS = 2.5f
        const val LABEL_TEXT_SIZE = 8f
        const val LABEL_INSET = 3f
        const val LABEL_GAP = 2f
        const val AXIS_GAP = 4f

        /** Con más puntos que estos, marcar cada uno taparía la línea. */
        const val MAX_POINTS_WITH_DOTS = 12
    }
}
