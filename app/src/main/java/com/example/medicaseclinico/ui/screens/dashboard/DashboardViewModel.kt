package com.example.medicaseclinico.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.data.local.AppDatabase
import com.example.medicaseclinico.data.repository.TemperatureRepositoryImpl
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.repository.TemperatureRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Cerebro del Dashboard de MediCase Clínico.
 *
 * Combina, dentro de viewModelScope:
 *  - la última lectura térmica (Room)
 *  - el último nivel de batería (Room)
 *  - un reloj interno que re-evalúa si los datos siguen siendo "en vivo"
 * y los transforma en un único DashboardUiState inmutable.
 *
 * @param repository contrato de dominio (no conoce Room directamente).
 * @param clock fuente de tiempo inyectable para tests deterministas.
 */
class DashboardViewModel(
    private val repository: TemperatureRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    /**
     * Estado observable por la UI.
     * WhileSubscribed(5_000): si la pantalla deja de observar (app en segundo
     * plano), los flujos se detienen tras 5 s → ahorro de batería. Los 5 s
     * cubren una rotación de pantalla sin reiniciar la recolección.
     */
    val uiState: StateFlow<DashboardUiState> =
        combine(
            repository.observeLatestReading(),
            repository.observeLatestBatteryLevel(),
            freshnessTicker()
        ) { latestReading, batteryLevel, _ ->
            buildUiState(latestReading, batteryLevel)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DashboardUiState.Loading()
        )

    // ------------------------------------------------------------------
    //  Lógica de negocio (fuera de los Composables)
    // ------------------------------------------------------------------
    /** Traduce los datos de Room al estado de pantalla correspondiente. */
    private fun buildUiState(
        latestReading: TemperatureReading?,
        batteryLevel: Int?
    ): DashboardUiState {
        // Sin lecturas: Empty State real, nunca valores inventados.
        if (latestReading == null) {
            return DashboardUiState.Empty(connectionStatus = ConnectionStatus.DISCONNECTED)
        }

        return DashboardUiState.Success(
            connectionStatus = resolveConnectionStatus(latestReading.timestamp),
            currentTemperature = latestReading.temperatureCelsius,
            temperatureStatus = TemperatureStatus.fromCelsius(latestReading.temperatureCelsius),
            batteryLevel = batteryLevel?.coerceIn(0, 100),
            lastReadingTimestamp = latestReading.timestamp
        )
    }

    /**
     * Deriva el estado del enlace a partir de la antigüedad de la última lectura.
     * El estuche transmite periódicamente: si no llega nada en LIVE_WINDOW_MS,
     * el enlace se considera perdido.
     */
    private fun resolveConnectionStatus(lastReadingTimestamp: Long): ConnectionStatus {
        val ageMs = clock() - lastReadingTimestamp
        return if (ageMs in 0..LIVE_WINDOW_MS) {
            ConnectionStatus.CONNECTED
        } else {
            ConnectionStatus.DISCONNECTED
        }
    }

    /**
     * Emite periódicamente para recalcular la frescura de los datos aunque
     * Room no cambie (si el estuche deja de transmitir, la tabla no emite,
     * pero el estado debe pasar a DISCONNECTED).
     */
    private fun freshnessTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(FRESHNESS_CHECK_INTERVAL_MS)
        }
    }

    // ------------------------------------------------------------------
    //  Constantes y Factory (inyección manual de dependencias)
    // ------------------------------------------------------------------
    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        /** Ventana en la que una lectura se considera "en vivo". */
        const val LIVE_WINDOW_MS = 60_000L
        /** Frecuencia de re-evaluación de la conexión (bajo consumo). */
        private const val FRESHNESS_CHECK_INTERVAL_MS = 15_000L

        /**
         * Construye el ViewModel con sus dependencias reales:
         * Application → AppDatabase → TemperatureDao → TemperatureRepositoryImpl.
         *
         * Uso en Compose: viewModel(factory = DashboardViewModel.Factory)
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                val dao = AppDatabase.getInstance(application).temperatureDao()
                DashboardViewModel(repository = TemperatureRepositoryImpl(dao))
            }
        }
    }
}