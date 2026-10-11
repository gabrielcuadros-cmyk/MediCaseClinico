package com.example.medicaseclinico.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.model.HistoryEvent
import com.example.medicaseclinico.domain.model.HistoryRange
import com.example.medicaseclinico.domain.model.MedicalReport
import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStats
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.repository.MedicalReportExporter
import com.example.medicaseclinico.domain.repository.TemperatureRepository
import com.example.medicaseclinico.domain.usecase.CalculateTemperatureStatsUseCase
import com.example.medicaseclinico.domain.usecase.DetectTemperatureEventsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Prepara el Historial Térmico: gráfica, estadísticas, línea de eventos y
 * temperatura actual, todo desde Room.
 *
 * El historial se lee resumido por intervalos y se recalcula como mucho cada
 * [refreshIntervalMs]: el estuche envía una lectura cada pocos segundos y
 * rehacer gráfica, estadísticas y eventos con cada una gastaría batería sin
 * que el usuario note la diferencia. La temperatura actual sí se actualiza
 * con cada lectura, porque es el dato clínico que no puede ir atrasado.
 *
 * @param clock se inyecta para que los tests fijen la hora y el rango sea
 * determinista.
 * @param computationDispatcher estadísticas y eventos se calculan fuera del
 * hilo principal para no congelar la pantalla.
 */
class TemperatureHistoryViewModel(
    private val temperatureRepository: TemperatureRepository,
    private val reportExporter: MedicalReportExporter,
    private val calculateStats: CalculateTemperatureStatsUseCase,
    private val detectEvents: DetectTemperatureEventsUseCase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS
) : ViewModel() {

    private val selectedRange = MutableStateFlow(HistoryRange.DEFAULT)
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TemperatureHistoryUiState> =
        combine(selectedRange, retryTrigger) { range, _ -> range }
            .flatMapLatest { range -> observeHistory(range) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TemperatureHistoryUiState.Loading(HistoryRange.DEFAULT)
            )

    private val _exportState = MutableStateFlow<ReportExportState>(ReportExportState.Idle)
    val exportState: StateFlow<ReportExportState> = _exportState.asStateFlow()

    /** El usuario tocó una pestaña del selector 24h / 7 días / 30 días. */
    fun onRangeSelected(range: HistoryRange) {
        selectedRange.value = range
    }

    /** Vuelve a consultar el mismo rango tras un error. */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

    /**
     * Genera el PDF con lo que el usuario tiene en pantalla en ese momento.
     * Sin lecturas no hay nada que reportar, y mientras se genera uno no se
     * lanza otro aunque se toque el botón varias veces.
     */
    fun exportReport() {
        val state = uiState.value as? TemperatureHistoryUiState.Success ?: return
        if (_exportState.value is ReportExportState.Exporting) return

        _exportState.value = ReportExportState.Exporting
        viewModelScope.launch {
            val report = MedicalReport(
                range = state.selectedRange,
                rangeStart = state.rangeStart,
                rangeEnd = state.rangeEnd,
                generatedAt = clock(),
                currentCelsius = state.current.celsius,
                currentReadingAt = state.current.timestamp,
                stats = state.stats,
                buckets = state.buckets,
                events = state.events
            )
            _exportState.value = reportExporter.export(report).fold(
                onSuccess = { uri -> ReportExportState.Ready(uri) },
                onFailure = { ReportExportState.Failed(ReportExportError.GENERATION) }
            )
        }
    }

    /** El menú de compartir se abrió, o el usuario ya vio el aviso de error. */
    fun onExportHandled() {
        _exportState.value = ReportExportState.Idle
    }

    /** El PDF se generó pero el teléfono no tiene con qué compartirlo. */
    fun onShareFailed() {
        _exportState.value = ReportExportState.Failed(ReportExportError.NO_SHARE_APP)
    }

    private fun observeHistory(range: HistoryRange): Flow<TemperatureHistoryUiState> {
        val rangeStart = range.startAt(clock())
        val history = temperatureRepository
            .observeBucketsSince(since = rangeStart, bucketMs = range.bucketMs)
            .throttleLatest(refreshIntervalMs)
            .map { buckets -> summarize(range, rangeStart, buckets) }
            .flowOn(computationDispatcher)

        return combine(
            history,
            temperatureRepository.observeLatestReading()
        ) { summary, latestReading ->
            buildUiState(range, summary, latestReading)
        }
            .onStart { emit(TemperatureHistoryUiState.Loading(range)) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(TemperatureHistoryUiState.Error(range))
            }
    }

    private fun summarize(
        range: HistoryRange,
        rangeStart: Long,
        buckets: List<TemperatureBucket>
    ): HistorySummary {
        val rangeEnd = clock()
        return HistorySummary(
            buckets = buckets,
            stats = calculateStats.fromBuckets(
                buckets = buckets,
                bucketMs = range.bucketMs,
                rangeEnd = rangeEnd
            ),
            events = detectEvents.fromBuckets(buckets),
            rangeStart = rangeStart,
            rangeEnd = rangeEnd
        )
    }

    private fun buildUiState(
        range: HistoryRange,
        summary: HistorySummary,
        latestReading: TemperatureReading?
    ): TemperatureHistoryUiState {
        val current = latestReading?.toCurrentTemperature()
        if (summary.stats == null || current == null) {
            return TemperatureHistoryUiState.Empty(selectedRange = range, current = current)
        }
        return TemperatureHistoryUiState.Success(
            selectedRange = range,
            current = current,
            buckets = summary.buckets,
            stats = summary.stats,
            events = summary.events,
            rangeStart = summary.rangeStart,
            rangeEnd = summary.rangeEnd
        )
    }

    private fun TemperatureReading.toCurrentTemperature() = CurrentTemperature(
        celsius = temperatureCelsius,
        status = TemperatureStatus.fromCelsius(temperatureCelsius),
        timestamp = timestamp
    )

    /** Parte costosa del estado, calculada una vez por refresco. */
    private data class HistorySummary(
        val buckets: List<TemperatureBucket>,
        val stats: TemperatureStats?,
        val events: List<HistoryEvent>,
        val rangeStart: Long,
        val rangeEnd: Long
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Intervalo mínimo entre dos recálculos del historial. */
        const val DEFAULT_REFRESH_INTERVAL_MS = 30_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                TemperatureHistoryViewModel(
                    temperatureRepository = application.container.temperatureRepository,
                    reportExporter = application.container.medicalReportExporter,
                    calculateStats = CalculateTemperatureStatsUseCase(),
                    detectEvents = DetectTemperatureEventsUseCase()
                )
            }
        }
    }
}

/**
 * Deja pasar el primer valor de inmediato y después como mucho uno cada
 * [periodMs], siempre el más reciente: la pantalla abre sin espera y no se
 * queda con datos viejos.
 */
private fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> =
    conflate().transform { value ->
        emit(value)
        delay(periodMs)
    }
