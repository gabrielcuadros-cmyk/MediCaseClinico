package com.example.medicaseclinico.ui.screens.dashboard

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.repository.TemperatureRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cerebro del Dashboard de MediCase Clínico con estado BLE real.
 */
class DashboardViewModel(
    private val temperatureRepository: TemperatureRepository,
    private val deviceRepository: CaseDeviceRepository
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> =
        retryTrigger
            .flatMapLatest { observeDashboard() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DashboardUiState.Loading()
            )

    fun retry() {
        deviceRepository.startMonitoring()
        retryTrigger.update { it + 1 }
    }

    private fun observeDashboard(): Flow<DashboardUiState> =
        combine(
            temperatureRepository.observeLatestReading(),
            temperatureRepository.observeLatestBatteryLevel(),
            deviceRepository.connectionStatus
        ) { latestReading, batteryLevel, connectionStatus ->
            buildUiState(latestReading, batteryLevel, connectionStatus)
        }
            .onStart {
                emit(DashboardUiState.Loading(connectionStatus = deviceRepository.connectionStatus.value))
            }
            .retryWhen { cause, attempt ->
                val isTransient = cause is IOException
                if (isTransient && attempt < MAX_AUTO_RETRIES) {
                    delay(BASE_RETRY_DELAY_MS shl attempt.toInt())
                    true
                } else {
                    false
                }
            }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(
                    DashboardUiState.Error(
                        connectionStatus = deviceRepository.connectionStatus.value,
                        reason = mapToErrorReason(throwable),
                        canRetry = true
                    )
                )
            }

    private fun buildUiState(
        latestReading: TemperatureReading?,
        batteryLevel: Int?,
        connectionStatus: ConnectionStatus
    ): DashboardUiState {
        if (latestReading == null) {
            return DashboardUiState.Empty(connectionStatus = connectionStatus)
        }
        return DashboardUiState.Success(
            connectionStatus = connectionStatus,
            currentTemperature = latestReading.temperatureCelsius,
            temperatureStatus = TemperatureStatus.fromCelsius(latestReading.temperatureCelsius),
            batteryLevel = batteryLevel?.coerceIn(0, 100),
            lastReadingTimestamp = latestReading.timestamp
        )
    }

    private fun mapToErrorReason(throwable: Throwable): ErrorReason = when (throwable) {
        is SQLiteException -> ErrorReason.DATABASE
        is SecurityException -> ErrorReason.BLUETOOTH
        is IOException -> ErrorReason.NETWORK
        else -> ErrorReason.UNKNOWN
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MAX_AUTO_RETRIES = 3L
        private const val BASE_RETRY_DELAY_MS = 1_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                DashboardViewModel(
                    temperatureRepository = application.container.temperatureRepository,
                    deviceRepository = application.container.caseDeviceRepository
                )
            }
        }
    }
}