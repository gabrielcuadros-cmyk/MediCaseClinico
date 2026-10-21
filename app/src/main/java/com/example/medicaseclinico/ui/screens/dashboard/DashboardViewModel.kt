package com.example.medicaseclinico.ui.screens.dashboard

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.model.CaseControlState
import com.example.medicaseclinico.domain.model.CaseGpsReading
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.CaseTransport
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.DeviceIssue
import com.example.medicaseclinico.domain.model.LockState
import com.example.medicaseclinico.domain.model.RelativeTime
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.repository.AlertEventRepository
import com.example.medicaseclinico.domain.repository.AuthRepository
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.repository.CaseLocationRepository
import com.example.medicaseclinico.domain.repository.LossModeRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cerebro del Dashboard de MediCase Clínico con estado BLE real.
 *
 * @param clock se inyecta para que los tests fijen la hora del tiempo relativo.
 * @param tickIntervalMs cada cuánto se recalcula "hace X min" aunque no
 * lleguen lecturas nuevas.
 */
class DashboardViewModel(
    private val temperatureRepository: TemperatureRepository,
    private val deviceRepository: CaseDeviceRepository,
    private val caseLocationRepository: CaseLocationRepository,
    private val alertEventRepository: AlertEventRepository,
    authRepository: AuthRepository,
    lossModeRepository: LossModeRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tickIntervalMs: Long = DEFAULT_TICK_INTERVAL_MS
) : ViewModel() {

    /** Última lectura de temperatura y de batería guardadas. */
    private data class Readings(val latest: TemperatureReading?, val batteryLevel: Int?)

    private data class QuickData(val lastLocationAt: Long?, val unreadAlerts: Int)

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

    /**
     * Nombre del usuario para el saludo "Hola, …", o null si no hay sesión o
     * la cuenta no tiene nombre: entonces no se saluda, no se inventa uno.
     */
    val userName: StateFlow<String?> =
        authRepository.currentUser
            .map { it?.displayName }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = null
            )

    /**
     * true mientras el Modo Pérdida está activo: el Inicio lo avisa arriba del
     * todo. Si el estado guardado no se puede leer, no se avisa de algo que no
     * se sabe.
     */
    val lossModeActive: StateFlow<Boolean> =
        lossModeRepository.state
            .map { it.isActive }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(false)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = false
            )

    /** Por dónde llegan los datos del estuche, para el pie "Sincronizado vía…". */
    val transport: CaseTransport = deviceRepository.transport

    /** Motivo actual por el que no hay enlace con el estuche (o null). */
    val deviceIssue: StateFlow<DeviceIssue?> = deviceRepository.deviceIssue

    fun retry() {
        deviceRepository.startMonitoring()
        retryTrigger.update { it + 1 }
    }

    /** Reanuda el monitoreo tras resolver un permiso o encender el BT. */
    fun onBlePrerequisiteResolved() {
        deviceRepository.startMonitoring()
    }

    /** Re-verifica requisitos cuando la pantalla regresa al primer plano. */
    fun onScreenResumed() {
        if (deviceRepository.deviceIssue.value?.isUserActionable == true) {
            deviceRepository.startMonitoring()
        }
    }

    private fun observeDashboard(): Flow<DashboardUiState> =
        combine(
            observeReadings(),
            deviceRepository.connectionStatus,
            deviceRepository.controlState,
            observeQuickData(),
            currentTime()
        ) { readings, connectionStatus, controlState, quickData, now ->
            buildUiState(readings, connectionStatus, controlState, quickData, now)
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

    private fun observeReadings(): Flow<Readings> =
        combine(
            temperatureRepository.observeLatestReading(),
            temperatureRepository.observeLatestBatteryLevel()
        ) { latest, batteryLevel -> Readings(latest = latest, batteryLevel = batteryLevel) }

    /** Lo que alimenta las "Secciones rápidas" que no está ya en el resto del Inicio. */
    private fun observeQuickData(): Flow<QuickData> =
        combine(
            deviceRepository.caseLocation,
            caseLocationRepository.observeLastKnownLocation(),
            alertEventRepository.observeUnreadCount()
        ) { liveLocation, lastKnown, unreadAlerts ->
            val live = liveLocation as? CaseLocationState.Reported
            QuickData(
                // La posición en vivo es siempre más reciente que la guardada.
                lastLocationAt = live?.takeIf { it.reading is CaseGpsReading.Fix }?.receivedAt
                    ?: lastKnown?.timestamp,
                unreadAlerts = unreadAlerts
            )
        }

    /**
     * Sin esto, con el estuche desconectado no llegan lecturas nuevas y el pie
     * se quedaría diciendo "hace 2 min" para siempre.
     */
    private fun currentTime(): Flow<Long> = flow {
        while (true) {
            emit(clock())
            delay(tickIntervalMs)
        }
    }

    private fun buildUiState(
        readings: Readings,
        connectionStatus: ConnectionStatus,
        controlState: CaseControlState,
        quickData: QuickData,
        now: Long
    ): DashboardUiState {
        val latestReading = readings.latest
        if (latestReading == null) {
            return DashboardUiState.Empty(connectionStatus = connectionStatus)
        }
        return DashboardUiState.Success(
            connectionStatus = connectionStatus,
            currentTemperature = latestReading.temperatureCelsius,
            temperatureStatus = TemperatureStatus.fromCelsius(latestReading.temperatureCelsius),
            batteryLevel = readings.batteryLevel?.coerceIn(0, 100),
            lockStatus = controlState.toLockStatus(),
            quickSections = QuickSectionsState(
                lastLocationElapsed = quickData.lastLocationAt?.let { at ->
                    RelativeTime.between(timestamp = at, now = now)
                },
                unreadAlerts = quickData.unreadAlerts,
                pinLock = controlState.toPinLockStatus()
            ),
            lastReadingElapsed = RelativeTime.between(
                timestamp = latestReading.timestamp,
                now = now
            )
        )
    }

    private fun CaseControlState.toLockStatus(): CaseLockStatus = when (this) {
        CaseControlState.Unavailable -> CaseLockStatus.UNKNOWN
        CaseControlState.NotSupported -> CaseLockStatus.NOT_SUPPORTED
        is CaseControlState.Ready -> when (lockState) {
            LockState.LOCKED -> CaseLockStatus.LOCKED
            LockState.OPEN -> CaseLockStatus.OPEN
            null -> CaseLockStatus.UNKNOWN
        }
    }

    private fun CaseControlState.toPinLockStatus(): PinLockStatus =
        when ((this as? CaseControlState.Ready)?.pinRequired) {
            true -> PinLockStatus.ENABLED
            false -> PinLockStatus.DISABLED
            null -> PinLockStatus.UNKNOWN
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

        /** El tiempo relativo cambia como mucho una vez por minuto. */
        const val DEFAULT_TICK_INTERVAL_MS = 60_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                DashboardViewModel(
                    temperatureRepository = application.container.temperatureRepository,
                    deviceRepository = application.container.caseDeviceRepository,
                    caseLocationRepository = application.container.caseLocationRepository,
                    alertEventRepository = application.container.alertEventRepository,
                    authRepository = application.container.authRepository,
                    lossModeRepository = application.container.lossModeRepository
                )
            }
        }
    }
}