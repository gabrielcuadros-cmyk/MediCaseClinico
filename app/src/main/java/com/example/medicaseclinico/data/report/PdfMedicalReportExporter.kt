package com.example.medicaseclinico.data.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.chart.buildStabilityChartModel
import com.example.medicaseclinico.domain.model.HistoryEvent
import com.example.medicaseclinico.domain.model.HistoryRange
import com.example.medicaseclinico.domain.model.MedicalReport
import com.example.medicaseclinico.domain.model.TemperatureExcursion
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.repository.MedicalReportExporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Genera el Reporte Médico como PDF con [PdfDocument] y lo deja en la caché
 * de la app, expuesto mediante FileProvider.
 *
 * Se guarda en caché y no en almacenamiento compartido porque contiene datos
 * de salud: solo sale del teléfono cuando el usuario elige con qué app
 * compartirlo, y no hace falta ningún permiso de almacenamiento.
 *
 * @param ioDispatcher hilo de fondo para escribir el archivo; se inyecta para
 * poder reemplazarlo en tests.
 */
class PdfMedicalReportExporter(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MedicalReportExporter {

    private val appContext = context.applicationContext

    override suspend fun export(report: MedicalReport): Result<String> =
        withContext(ioDispatcher) {
            try {
                Result.success(writeReport(report))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun writeReport(report: MedicalReport): String {
        val directory = File(appContext.cacheDir, REPORTS_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("No se pudo crear la carpeta de reportes")
        }
        deleteOldReports(directory, now = report.generatedAt)

        val file = File(directory, fileNameFor(report.generatedAt))
        val document = PdfDocument()
        try {
            val writer = ReportWriter(
                document = document,
                footerText = appContext.getString(R.string.report_footer),
                pageLabel = { number -> appContext.getString(R.string.report_page, number) }
            )
            drawReport(writer, report)
            writer.finish()
            file.outputStream().use { output -> document.writeTo(output) }
        } finally {
            document.close()
        }

        return FileProvider
            .getUriForFile(appContext, appContext.packageName + AUTHORITY_SUFFIX, file)
            .toString()
    }

    /**
     * Los reportes recientes se conservan: una app de correo puede seguir
     * leyendo el archivo unos segundos después de compartirlo.
     */
    private fun deleteOldReports(directory: File, now: Long) {
        directory.listFiles()
            ?.filter { now - it.lastModified() > REPORT_RETENTION_MS }
            ?.forEach { it.delete() }
    }

    private fun fileNameFor(generatedAt: Long): String {
        val stamp = DateTimeFormatter.ofPattern(FILE_STAMP_PATTERN, Locale.ROOT)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(generatedAt))
        return "$FILE_PREFIX$stamp$FILE_EXTENSION"
    }

    // ------------------------------------------------------------------
    //  Contenido del documento
    // ------------------------------------------------------------------
    private fun drawReport(writer: ReportWriter, report: MedicalReport) {
        val style = ReportStyle()
        val dateTime = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN, Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        fun formatDateTime(timestamp: Long) = dateTime.format(Instant.ofEpochMilli(timestamp))
        fun formatCelsius(celsius: Float) = appContext.getString(R.string.temperature_value, celsius)

        // Cabecera: qué es, cuándo se generó y qué periodo cubre.
        writer.text(appContext.getString(R.string.report_title), style.title, spacingAfter = 2f)
        writer.text(appContext.getString(R.string.app_name), style.muted, spacingAfter = 12f)
        writer.text(
            appContext.getString(R.string.report_generated_at, formatDateTime(report.generatedAt)),
            style.body
        )
        writer.text(
            appContext.getString(
                R.string.report_period,
                formatDateTime(report.rangeStart),
                formatDateTime(report.rangeEnd),
                appContext.getString(report.range.labelRes())
            ),
            style.body,
            spacingAfter = SECTION_SPACING
        )

        // Resumen estadístico.
        val stats = report.stats
        val monitoredMinutes = TimeUnit.MILLISECONDS.toMinutes(stats.monitoredDurationMs)
        writer.text(appContext.getString(R.string.report_section_summary), style.heading, spacingAfter = 6f)
        val rows = listOf(
            R.string.report_label_current to appContext.getString(
                R.string.report_value_current,
                formatCelsius(report.currentCelsius),
                formatDateTime(report.currentReadingAt)
            ),
            R.string.report_label_min to formatCelsius(stats.minCelsius),
            R.string.report_label_max to formatCelsius(stats.maxCelsius),
            R.string.report_label_average to formatCelsius(stats.averageCelsius),
            R.string.report_label_time_in_range to appContext.getString(
                R.string.report_value_percent,
                stats.timeInSafeRangePercent
            ),
            R.string.report_label_monitored to appContext.getString(
                R.string.report_value_duration,
                monitoredMinutes / MINUTES_PER_HOUR,
                monitoredMinutes % MINUTES_PER_HOUR
            ),
            R.string.report_label_samples to stats.sampleCount.toString(),
            R.string.report_label_safe_range to appContext.getString(
                R.string.report_value_safe_range,
                TemperatureStatus.MIN_SAFE_CELSIUS,
                TemperatureStatus.MAX_SAFE_CELSIUS
            )
        )
        for ((labelRes, value) in rows) {
            writer.labelValue(appContext.getString(labelRes), value, style.muted, style.body)
        }
        writer.space(SECTION_SPACING)

        // Gráfica de estabilidad, la misma que en pantalla.
        writer.ensureSpace(style.heading.fontSpacing + 6f + CHART_BLOCK_HEIGHT)
        writer.text(
            appContext.getString(
                R.string.history_chart_title,
                TemperatureStatus.MIN_SAFE_CELSIUS,
                TemperatureStatus.MAX_SAFE_CELSIUS
            ),
            style.heading,
            spacingAfter = 6f
        )
        val chartModel = buildStabilityChartModel(
            buckets = report.buckets,
            bucketMs = report.range.bucketMs,
            rangeStart = report.rangeStart,
            rangeEnd = report.rangeEnd
        )
        val chartDrawer = PdfStabilityChartDrawer(
            maxSafeLabel = appContext.getString(
                R.string.history_chart_max_safe,
                TemperatureStatus.MAX_SAFE_CELSIUS
            ),
            minSafeLabel = appContext.getString(
                R.string.history_chart_min_safe,
                TemperatureStatus.MIN_SAFE_CELSIUS
            )
        )
        writer.block(CHART_BLOCK_HEIGHT) { canvas, top ->
            chartDrawer.draw(
                canvas = canvas,
                area = RectF(MARGIN, top, PAGE_WIDTH - MARGIN, top + CHART_HEIGHT),
                model = chartModel,
                axisLabels = axisLabels(report)
            )
        }
        writer.space(SECTION_SPACING)

        // Línea de eventos.
        writer.text(appContext.getString(R.string.history_events_section), style.heading, spacingAfter = 6f)
        if (report.events.isEmpty()) {
            writer.text(appContext.getString(R.string.report_events_empty), style.body)
        }
        for (event in report.events) {
            // Título y descripción de un evento nunca se separan entre dos páginas.
            writer.ensureSpace(style.bodyBold.fontSpacing + style.body.fontSpacing + 8f)
            writer.text(
                appContext.getString(
                    R.string.report_event_heading,
                    formatDateTime(event.timestamp),
                    event.title()
                ),
                style.bodyBold,
                spacingAfter = 1f
            )
            writer.text(event.description(::formatCelsius), style.body, spacingAfter = 7f)
        }
    }

    private fun axisLabels(report: MedicalReport): List<String> {
        val pattern = if (report.range == HistoryRange.LAST_24_HOURS) HOUR_PATTERN else DAY_PATTERN
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        val lastIndex = AXIS_LABEL_COUNT - 1
        return List(AXIS_LABEL_COUNT) { index ->
            val timestamp = report.rangeStart + (report.rangeEnd - report.rangeStart) * index / lastIndex
            formatter.format(Instant.ofEpochMilli(timestamp))
        }
    }

    private fun HistoryRange.labelRes(): Int = when (this) {
        HistoryRange.LAST_24_HOURS -> R.string.history_range_24h
        HistoryRange.LAST_7_DAYS -> R.string.history_range_7d
        HistoryRange.LAST_30_DAYS -> R.string.history_range_30d
    }

    private fun HistoryEvent.title(): String = when (this) {
        is HistoryEvent.TemperatureAlert -> appContext.getString(R.string.history_event_alert_title)
        is HistoryEvent.TemperatureNormalized ->
            appContext.getString(R.string.history_event_normalized_title)
        is HistoryEvent.BatteryLow -> appContext.getString(R.string.history_event_battery_title)
    }

    private fun HistoryEvent.description(formatCelsius: (Float) -> String): String = when (this) {
        is HistoryEvent.TemperatureAlert -> {
            val peak = formatCelsius(peakCelsius)
            val peakDescription = when (direction) {
                TemperatureExcursion.HIGH ->
                    appContext.getString(R.string.history_event_alert_high, peak)
                TemperatureExcursion.LOW ->
                    appContext.getString(R.string.history_event_alert_low, peak)
            }
            if (endTimestamp == null) {
                appContext.getString(R.string.history_event_alert_ongoing, peakDescription)
            } else {
                peakDescription
            }
        }
        is HistoryEvent.TemperatureNormalized -> appContext.getString(
            R.string.history_event_normalized_description,
            formatCelsius(celsius)
        )
        is HistoryEvent.BatteryLow ->
            appContext.getString(R.string.history_event_battery_description, levelPercent)
    }

    /** Tipografías del documento. Se crean por reporte: Paint no es seguro entre hilos. */
    private class ReportStyle {
        val title = textPaint(size = 18f, color = TEXT_COLOR, bold = true)
        val heading = textPaint(size = 10f, color = MUTED_COLOR, bold = true)
        val body = textPaint(size = 10.5f, color = TEXT_COLOR, bold = false)
        val bodyBold = textPaint(size = 10.5f, color = TEXT_COLOR, bold = true)
        val muted = textPaint(size = 10.5f, color = MUTED_COLOR, bold = false)
    }

    /**
     * Escribe de arriba abajo y abre una página nueva cuando el contenido no
     * cabe. Cada página lleva el pie con el aviso legal y su número.
     */
    private class ReportWriter(
        private val document: PdfDocument,
        private val footerText: String,
        private val pageLabel: (Int) -> String
    ) {
        private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED_COLOR
            textSize = FOOTER_TEXT_SIZE
        }
        private val rulePaint = Paint().apply {
            color = RULE_COLOR
            strokeWidth = 0.75f
        }
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var y = MARGIN

        private val canvas: Canvas
            get() = checkNotNull(page) { "El reporte ya se cerró" }.canvas

        init {
            startPage()
        }

        fun finish() {
            page?.let { document.finishPage(it) }
            page = null
        }

        fun ensureSpace(height: Float) {
            if (y + height > CONTENT_BOTTOM) {
                finish()
                startPage()
            }
        }

        fun space(height: Float) {
            y += height
        }

        fun text(text: String, paint: Paint, spacingAfter: Float = 4f) {
            ensureSpace(paint.fontSpacing)
            canvas.drawText(text, MARGIN, y - paint.ascent(), paint)
            y += paint.fontSpacing + spacingAfter
        }

        fun labelValue(label: String, value: String, labelPaint: Paint, valuePaint: Paint) {
            ensureSpace(valuePaint.fontSpacing)
            canvas.drawText(label, MARGIN, y - labelPaint.ascent(), labelPaint)
            canvas.drawText(value, VALUE_COLUMN_X, y - valuePaint.ascent(), valuePaint)
            y += valuePaint.fontSpacing + 3f
        }

        /** Reserva [height] puntos y deja que [draw] pinte desde la Y indicada. */
        fun block(height: Float, draw: (Canvas, Float) -> Unit) {
            ensureSpace(height)
            draw(canvas, y)
            y += height
        }

        private fun startPage() {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber)
                .create()
            page = document.startPage(info)
            y = MARGIN
            drawFooter()
        }

        private fun drawFooter() {
            val contentWidth = (PAGE_WIDTH - 2 * MARGIN).toInt()
            canvas.drawLine(MARGIN, FOOTER_TOP - 6f, PAGE_WIDTH - MARGIN, FOOTER_TOP - 6f, rulePaint)

            val layout = StaticLayout.Builder
                .obtain(footerText, 0, footerText.length, footerPaint, contentWidth)
                .build()
            canvas.save()
            canvas.translate(MARGIN, FOOTER_TOP)
            layout.draw(canvas)
            canvas.restore()

            val label = pageLabel(pageNumber)
            canvas.drawText(
                label,
                PAGE_WIDTH - MARGIN - footerPaint.measureText(label),
                PAGE_HEIGHT - MARGIN,
                footerPaint
            )
        }
    }

    private companion object {
        const val REPORTS_DIRECTORY = "reports"
        const val AUTHORITY_SUFFIX = ".fileprovider"
        const val FILE_PREFIX = "reporte-medicase-"
        const val FILE_EXTENSION = ".pdf"
        const val FILE_STAMP_PATTERN = "yyyyMMdd-HHmmss"
        val REPORT_RETENTION_MS: Long = TimeUnit.HOURS.toMillis(1)

        const val DATE_TIME_PATTERN = "d MMM yyyy, HH:mm"
        const val HOUR_PATTERN = "HH:mm"
        const val DAY_PATTERN = "d MMM"
        const val AXIS_LABEL_COUNT = 5
        const val MINUTES_PER_HOUR = 60L

        // Página A4 en puntos PDF (1/72 de pulgada).
        const val PAGE_WIDTH = 595f
        const val PAGE_HEIGHT = 842f
        const val MARGIN = 40f
        const val VALUE_COLUMN_X = MARGIN + 190f
        const val SECTION_SPACING = 16f
        const val CHART_HEIGHT = 170f
        const val CHART_BLOCK_HEIGHT = CHART_HEIGHT + 18f
        const val FOOTER_TEXT_SIZE = 8f
        const val FOOTER_TOP = PAGE_HEIGHT - MARGIN - 36f
        const val CONTENT_BOTTOM = FOOTER_TOP - 16f

        const val TEXT_COLOR = 0xFF14181F.toInt()
        const val MUTED_COLOR = 0xFF6B7280.toInt()
        const val RULE_COLOR = 0xFFE3E7EE.toInt()

        fun textPaint(size: Float, color: Int, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }
}
