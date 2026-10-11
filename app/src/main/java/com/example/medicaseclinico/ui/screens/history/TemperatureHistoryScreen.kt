package com.example.medicaseclinico.ui.screens.history

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.HistoryEvent
import com.example.medicaseclinico.domain.model.HistoryRange
import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureExcursion
import com.example.medicaseclinico.domain.model.TemperatureStats
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.ui.components.MediCaseBackButton
import com.example.medicaseclinico.ui.components.MediCaseLoadingState
import com.example.medicaseclinico.ui.components.MediCaseMessageState
import com.example.medicaseclinico.ui.screens.history.components.CurrentTemperatureCard
import com.example.medicaseclinico.ui.screens.history.components.EventTimelineItem
import com.example.medicaseclinico.ui.screens.history.components.HistoryRangeSelector
import com.example.medicaseclinico.ui.screens.history.components.StabilityChartCard
import com.example.medicaseclinico.ui.theme.MediCaseTheme

private val ScreenPadding = 16.dp
private val SectionSpacing = 16.dp
private const val PDF_MIME_TYPE = "application/pdf"

/**
 * Entrada de la pestaña Temp: conecta el ViewModel con la pantalla sin estado.
 */
@Composable
fun TemperatureHistoryRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TemperatureHistoryViewModel = viewModel(factory = TemperatureHistoryViewModel.Factory)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val shareTitle = stringResource(R.string.history_share_title)

    // Abrir el menú de compartir es un efecto de una sola vez: se confirma al
    // ViewModel para que una rotación de pantalla no lo vuelva a lanzar.
    LaunchedEffect(exportState) {
        val ready = exportState as? ReportExportState.Ready ?: return@LaunchedEffect
        if (context.shareReport(uri = ready.uri, chooserTitle = shareTitle)) {
            viewModel.onExportHandled()
        } else {
            viewModel.onShareFailed()
        }
    }

    TemperatureHistoryScreen(
        uiState = uiState,
        exportState = exportState,
        onBack = onBack,
        onRangeSelected = viewModel::onRangeSelected,
        onRetry = viewModel::retry,
        onExportReport = viewModel::exportReport,
        onExportErrorShown = viewModel::onExportHandled,
        modifier = modifier
    )
}

/**
 * Abre el menú de compartir de Android con el PDF. Protegido: si ninguna app
 * puede recibirlo o se rechaza el permiso de lectura, devuelve false en lugar
 * de cerrar la app.
 */
private fun Context.shareReport(uri: String, chooserTitle: String): Boolean {
    val reportUri = Uri.parse(uri)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = PDF_MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, reportUri)
        clipData = ClipData.newRawUri(null, reportUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        startActivity(Intent.createChooser(sendIntent, chooserTitle))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

/**
 * Pantalla Historial Térmico: temperatura actual, selector de rango, gráfica
 * de estabilidad y línea de eventos.
 *
 * El selector de rango está presente en todos los estados: si un rango no
 * tiene lecturas o falla, el usuario puede probar otro sin salir de la pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemperatureHistoryScreen(
    uiState: TemperatureHistoryUiState,
    exportState: ReportExportState,
    onBack: () -> Unit,
    onRangeSelected: (HistoryRange) -> Unit,
    onRetry: () -> Unit,
    onExportReport: () -> Unit,
    onExportErrorShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val exportFailure = exportState as? ReportExportState.Failed
    val exportErrorMessage = when (exportFailure?.reason) {
        ReportExportError.GENERATION -> stringResource(R.string.history_export_error)
        ReportExportError.NO_SHARE_APP -> stringResource(R.string.history_share_error)
        null -> null
    }

    LaunchedEffect(exportErrorMessage) {
        if (exportErrorMessage != null) {
            snackbarHostState.showSnackbar(exportErrorMessage)
            onExportErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.history_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    MediCaseBackButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (uiState) {
            is TemperatureHistoryUiState.Loading ->
                HistoryMessageLayout(
                    selectedRange = uiState.selectedRange,
                    onRangeSelected = onRangeSelected,
                    modifier = contentModifier
                ) { messageModifier ->
                    MediCaseLoadingState(
                        message = stringResource(R.string.history_loading),
                        modifier = messageModifier
                    )
                }

            is TemperatureHistoryUiState.Empty ->
                HistoryMessageLayout(
                    selectedRange = uiState.selectedRange,
                    onRangeSelected = onRangeSelected,
                    modifier = contentModifier,
                    header = { CurrentTemperatureCard(current = uiState.current) }
                ) { messageModifier ->
                    MediCaseMessageState(
                        icon = Icons.Outlined.DeviceThermostat,
                        title = stringResource(R.string.history_empty_title),
                        message = stringResource(R.string.history_empty_message),
                        accentColor = MaterialTheme.colorScheme.primary,
                        accentContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = messageModifier
                    )
                }

            is TemperatureHistoryUiState.Error ->
                HistoryMessageLayout(
                    selectedRange = uiState.selectedRange,
                    onRangeSelected = onRangeSelected,
                    modifier = contentModifier
                ) { messageModifier ->
                    MediCaseMessageState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = stringResource(R.string.history_error_title),
                        message = stringResource(R.string.history_error_message),
                        accentColor = MaterialTheme.colorScheme.error,
                        accentContainerColor = MaterialTheme.colorScheme.errorContainer,
                        modifier = messageModifier,
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = onRetry
                    )
                }

            is TemperatureHistoryUiState.Success ->
                HistoryContent(
                    uiState = uiState,
                    isExporting = exportState is ReportExportState.Exporting,
                    onRangeSelected = onRangeSelected,
                    onExportReport = onExportReport,
                    modifier = contentModifier
                )
        }
    }
}

/**
 * Estructura común de Loading, Empty y Error: subtítulo, cabecera opcional y
 * selector fijos arriba, y el mensaje del estado ocupando el resto.
 */
@Composable
private fun HistoryMessageLayout(
    selectedRange: HistoryRange,
    onRangeSelected: (HistoryRange) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    message: @Composable (Modifier) -> Unit
) {
    Column(modifier = modifier.padding(horizontal = ScreenPadding)) {
        HistorySubtitle()
        Spacer(modifier = Modifier.height(SectionSpacing))
        if (header != null) {
            header()
            Spacer(modifier = Modifier.height(SectionSpacing))
        }
        HistoryRangeSelector(selectedRange = selectedRange, onRangeSelected = onRangeSelected)
        message(Modifier.weight(1f))
    }
}

/** Lista perezosa: un rango de 30 días puede traer muchos eventos. */
@Composable
private fun HistoryContent(
    uiState: TemperatureHistoryUiState.Success,
    isExporting: Boolean,
    onRangeSelected: (HistoryRange) -> Unit,
    onExportReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = ScreenPadding,
            end = ScreenPadding,
            bottom = ScreenPadding
        )
    ) {
        item {
            HistorySubtitle()
            Spacer(modifier = Modifier.height(SectionSpacing))
            CurrentTemperatureCard(current = uiState.current)
            Spacer(modifier = Modifier.height(SectionSpacing))
            HistoryRangeSelector(
                selectedRange = uiState.selectedRange,
                onRangeSelected = onRangeSelected
            )
            Spacer(modifier = Modifier.height(SectionSpacing))
            StabilityChartCard(
                buckets = uiState.buckets,
                stats = uiState.stats,
                range = uiState.selectedRange,
                rangeStart = uiState.rangeStart,
                rangeEnd = uiState.rangeEnd
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.history_events_section),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (uiState.events.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.history_events_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            itemsIndexed(uiState.events) { index, event ->
                EventTimelineItem(
                    event = event,
                    referenceTime = uiState.rangeEnd,
                    isLast = index == uiState.events.lastIndex
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
            ExportReportButton(isExporting = isExporting, onClick = onExportReport)
        }
    }
}

/**
 * Botón "Exportar Reporte Médico (PDF)" del diseño: ancho, azul claro, con
 * ícono. Mientras se genera el PDF se desactiva y muestra el progreso, para
 * que el usuario sepa que su toque se registró y no lo repita.
 */
@Composable
private fun ExportReportButton(
    isExporting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isExporting,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        if (isExporting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.UploadFile,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(
                if (isExporting) R.string.history_export_in_progress else R.string.history_export_report
            ),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HistorySubtitle(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.history_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

// ------------------------------------------------------------------
//  Previews (valores de ejemplo solo para el IDE)
// ------------------------------------------------------------------
private const val PREVIEW_NOW = 1_760_000_000_000L
private const val PREVIEW_HOUR_MS = 3_600_000L
private val PreviewRange = HistoryRange.LAST_24_HOURS

private val PreviewCurrent = CurrentTemperature(
    celsius = 4.2f,
    status = TemperatureStatus.SAFE,
    timestamp = PREVIEW_NOW
)

private fun previewSuccessState(): TemperatureHistoryUiState.Success {
    val averages = listOf(4.3f, 4.1f, 4.2f, 3.9f, 4.0f, 4.5f, 4.7f, 4.1f, 4.0f, 4.2f)
    val rangeStart = PreviewRange.startAt(PREVIEW_NOW)
    val stepMs = PreviewRange.durationMs / averages.size
    val buckets = averages.mapIndexed { index, average ->
        TemperatureBucket(
            startTimestamp = rangeStart + index * stepMs,
            averageCelsius = average,
            minCelsius = average - 0.2f,
            maxCelsius = average + 0.2f,
            sampleCount = 12,
            outOfRangeCount = 0
        )
    }
    return TemperatureHistoryUiState.Success(
        selectedRange = PreviewRange,
        current = PreviewCurrent,
        buckets = buckets,
        stats = TemperatureStats(
            sampleCount = 120,
            minCelsius = 3.7f,
            maxCelsius = 4.9f,
            averageCelsius = 4.2f,
            timeInSafeRangePercent = 100f,
            monitoredDurationMs = PreviewRange.durationMs
        ),
        events = listOf(
            HistoryEvent.TemperatureNormalized(
                timestamp = PREVIEW_NOW - 2 * PREVIEW_HOUR_MS,
                celsius = 4.2f,
                recoveredFrom = TemperatureExcursion.HIGH
            ),
            HistoryEvent.TemperatureAlert(
                timestamp = PREVIEW_NOW - 3 * PREVIEW_HOUR_MS,
                direction = TemperatureExcursion.HIGH,
                peakCelsius = 8.1f,
                endTimestamp = PREVIEW_NOW - 2 * PREVIEW_HOUR_MS
            ),
            HistoryEvent.BatteryLow(
                timestamp = PREVIEW_NOW - 20 * PREVIEW_HOUR_MS,
                levelPercent = 18
            )
        ),
        rangeStart = rangeStart,
        rangeEnd = PREVIEW_NOW
    )
}

@Composable
private fun HistoryScreenPreview(
    uiState: TemperatureHistoryUiState,
    exportState: ReportExportState = ReportExportState.Idle
) {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            TemperatureHistoryScreen(
                uiState = uiState,
                exportState = exportState,
                onBack = {},
                onRangeSelected = {},
                onRetry = {},
                onExportReport = {},
                onExportErrorShown = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Historial - Loading")
@Composable
private fun TemperatureHistoryLoadingPreview() {
    HistoryScreenPreview(TemperatureHistoryUiState.Loading(selectedRange = PreviewRange))
}

@Preview(showBackground = true, name = "Historial - Empty")
@Composable
private fun TemperatureHistoryEmptyPreview() {
    HistoryScreenPreview(
        TemperatureHistoryUiState.Empty(selectedRange = PreviewRange, current = PreviewCurrent)
    )
}

@Preview(showBackground = true, name = "Historial - Error")
@Composable
private fun TemperatureHistoryErrorPreview() {
    HistoryScreenPreview(TemperatureHistoryUiState.Error(selectedRange = PreviewRange))
}

@Preview(showBackground = true, name = "Historial - Success")
@Composable
private fun TemperatureHistorySuccessPreview() {
    HistoryScreenPreview(previewSuccessState())
}

@Preview(showBackground = true, name = "Historial - Generando reporte")
@Composable
private fun TemperatureHistoryExportingPreview() {
    HistoryScreenPreview(previewSuccessState(), exportState = ReportExportState.Exporting)
}
