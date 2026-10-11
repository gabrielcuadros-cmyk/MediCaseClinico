package com.example.medicaseclinico.ui.screens.dashboard

import android.database.sqlite.SQLiteException
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cerebro del Dashboard de MediCase Clínico.
 *
 * Combina, dentro de viewModelScope:
 *  - la última lectura térmica (Room)
 *  - el último nivel de batería (Room)
 *  - un reloj interno que re-evalúa si los datos siguen siendo "en vivo"
 * y los transforma en un único DashboardUiState inmutable.
 *
 * Ninguna excepción del flujo llega a la UI: se transforma en estado Error.
 *
 * @param repository contrato de dominio (no conoce Room directamente).
 * @param clock fuente de tiempo inyectable para tests deterministas.
 */
class DashboardViewModel(
    private val repository: TemperatureRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    // NUEVO (Commit 14): cada incremento reinicia la observación desde cero.
    private val retryTrigger = MutableStateFlow(0)

    /**
     * Estado observable por la UI.
     * flatMapLatest: al pulsar "Reintentar", cancela el flujo anterior
     * (posiblemente fallido) y arranca uno nuevo limpio.
     * WhileSubscribed(5_000): si la pantalla deja de observar (app en segundo
     * plano), los flujos se detienen tras 5 s → ahorro de batería.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> =
        retryTrigger
            .flatMapLatest { observeDashboard() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DashboardUiState.Loading()
            )

    // ------------------------------------------------------------------
    //  Eventos de la UI
    // ------------------------------------------------------------------
    /** NUEVO (Commit 14): invocado por el botón "Reintentar" de la pantalla de error. */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

    // ------------------------------------------------------------------
    //  Flujo principal con manejo de errores
    // ------------------------------------------------------------------
    private fun observeDashboard(): Flow<DashboardUiState> =
        combine(
            repository.observeLatestReading(),
            repository.observeLatestBatteryLevel(),
            freshnessTicker()
        ) { latestReading, batteryLevel, _ ->
            buildUiState(latestReading, batteryLevel)
        }
            // NUEVO (Commit 14): muestra Loading al iniciar cada intento.
            .onStart { emit(DashboardUiState.Loading()) }
            // NUEVO (Commit 14): reintento automático solo para fallos transitorios.
            .retryWhen { cause, attempt ->
                val isTransient = cause is IOException
                if (isTransient && attempt < MAX_AUTO_RETRIES) {
                    // Espera progresiva: 1 s, 2 s, 4 s → no satura batería ni radio.
                    delay(BASE_RETRY_DELAY_MS shl attempt.toInt())
                    true
                } else {
                    false
                }
            }
            // NUEVO (Commit 14): cualquier excepción restante → estado Error, sin crash.
            .catch { throwable ->
                // La cancelación (salir de la pantalla) no es un error: se propaga.
                if (throwable is CancellationException) throw throwable
                emit(
                    DashboardUiState.Error(
                        connectionStatus = ConnectionStatus.DISCONNECTED,
                        reason = mapToErrorReason(throwable),
                        canRetry = true
                    )
                )
            }

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
     * Se reemplazará por el estado real del gestor BLE cuando se integre esa capa.
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
     * NUEVO (Commit 14): clasifica la excepción para que la UI muestre el
     * mensaje e icono correctos sin conocer detalles técnicos.
     */
    private fun mapToErrorReason(throwable: Throwable): ErrorReason = when (throwable) {
        // Base de datos corrupta, disco lleno, tabla inexistente...
        is SQLiteException -> ErrorReason.DATABASE
        // Permisos BLUETOOTH_CONNECT / BLUETOOTH_SCAN denegados en Android 12+.
        is SecurityException -> ErrorReason.BLUETOOTH
        // Sin red / timeout al sincronizar con Firebase (tras agotar reintentos).
        is IOException -> ErrorReason.NETWORK
        else -> ErrorReason.UNKNOWN
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

        // NUEVO (Commit 14): política de reintento automático.
        private const val MAX_AUTO_RETRIES = 3L
        private const val BASE_RETRY_DELAY_MS = 1_000L

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