package com.example.medicaseclinico.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.model.HistoryRange
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.repository.TemperatureRepository
import com.example.medicaseclinico.domain.usecase.CalculateTemperatureStatsUseCase
import com.example.medicaseclinico.domain.usecase.DetectTemperatureEventsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

/**
 * Prepara el Historial Térmico: lecturas del rango elegido, estadísticas,
 * línea de eventos y temperatura actual, todo desde Room.
 *
 * @param clock se inyecta para que los tests fijen la hora y el rango sea
 * determinista.
 * @param computationDispatcher estadísticas y eventos recorren todas las
 * lecturas del rango (hasta 30 días); se calculan fuera del hilo principal
 * para no congelar la pantalla.
 */
class TemperatureHistoryViewModel(
    private val temperatureRepository: TemperatureRepository,
    private val calculateStats: CalculateTemperatureStatsUseCase,
    private val detectEvents: DetectTemperatureEventsUseCase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default
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

    /** El usuario tocó una pestaña del selector 24h / 7 días / 30 días. */
    fun onRangeSelected(range: HistoryRange) {
        selectedRange.value = range
    }

    /** Vuelve a consultar el mismo rango tras un error. */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

    private fun observeHistory(range: HistoryRange): Flow<TemperatureHistoryUiState> {
        val rangeStart = range.startAt(clock())
        return combine(
            temperatureRepository.observeReadingsSince(rangeStart),
            temperatureRepository.observeLatestReading()
        ) { readings, latestReading ->
            buildUiState(range, rangeStart, readings, latestReading)
        }
            .onStart { emit(TemperatureHistoryUiState.Loading(range)) }
            .flowOn(computationDispatcher)
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(TemperatureHistoryUiState.Error(range))
            }
    }

    private fun buildUiState(
        range: HistoryRange,
        rangeStart: Long,
        readings: List<TemperatureReading>,
        latestReading: TemperatureReading?
    ): TemperatureHistoryUiState {
        val rangeEnd = clock()
        val stats = calculateStats(readings, rangeEnd)
        val current = (latestReading ?: readings.lastOrNull())?.toCurrentTemperature()

        if (stats == null || current == null) {
            return TemperatureHistoryUiState.Empty(selectedRange = range, current = current)
        }
        return TemperatureHistoryUiState.Success(
            selectedRange = range,
            current = current,
            readings = readings,
            stats = stats,
            events = detectEvents(readings),
            rangeStart = rangeStart,
            rangeEnd = rangeEnd
        )
    }

    private fun TemperatureReading.toCurrentTemperature() = CurrentTemperature(
        celsius = temperatureCelsius,
        status = TemperatureStatus.fromCelsius(temperatureCelsius),
        timestamp = timestamp
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                TemperatureHistoryViewModel(
                    temperatureRepository = application.container.temperatureRepository,
                    calculateStats = CalculateTemperatureStatsUseCase(),
                    detectEvents = DetectTemperatureEventsUseCase()
                )
            }
        }
    }
}
