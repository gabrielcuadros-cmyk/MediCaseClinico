package com.example.medicaseclinico.data.sync

import com.example.medicaseclinico.domain.alert.AlertPublisher
import com.example.medicaseclinico.domain.model.CaseGpsReading
import com.example.medicaseclinico.domain.model.CaseLocation
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.CaseTelemetry
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.repository.CaseLocationRepository
import com.example.medicaseclinico.domain.repository.TemperatureRepository
import com.example.medicaseclinico.domain.usecase.ShouldSaveCaseLocationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Orquestador entre el estuche en vivo, el historial y las alertas.
 */
class CaseTelemetrySynchronizer(
    private val deviceRepository: CaseDeviceRepository,
    private val temperatureRepository: TemperatureRepository,
    private val caseLocationRepository: CaseLocationRepository,
    private val alertPublisher: AlertPublisher,
    private val shouldSaveLocation: ShouldSaveCaseLocationUseCase,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var syncJob: Job? = null

    // Estado del muestreo (solo se accede desde la corrutina de telemetría).
    private var latestBatteryPercent: Int? = null
    private var batteryChangedSinceLastSave = false
    private var lastSavedAt: Long = 0L
    private var lastSavedStatus: TemperatureStatus? = null

    // Solo se accede desde la corrutina de ubicación.
    private var lastSavedLocation: CaseLocation? = null

    /** Inicia la sincronización y la vigilancia de conexión. Idempotente. */
    fun start() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            purgeExpiredReadings()
            purgeExpiredLocations()
            launch { collectTelemetry() }
            launch { collectCaseLocation() }
            launch { watchConnection() }
        }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
    }

    // ------------------------------------------------------------------
    //  Telemetría
    // ------------------------------------------------------------------
    private suspend fun collectTelemetry() {
        try {
            deviceRepository.telemetry.collect { telemetry ->
                when (telemetry) {
                    is CaseTelemetry.Battery -> onBattery(telemetry)
                    is CaseTelemetry.Temperature -> onTemperature(telemetry)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            syncJob = null
        }
    }

    private fun onBattery(battery: CaseTelemetry.Battery) {
        alertPublisher.onBatteryLevel(battery.levelPercent)
        if (battery.levelPercent != latestBatteryPercent) {
            latestBatteryPercent = battery.levelPercent
            batteryChangedSinceLastSave = true
        }
    }

    private suspend fun onTemperature(sample: CaseTelemetry.Temperature) {
        val status = TemperatureStatus.fromCelsius(sample.celsius)
        alertPublisher.onTemperature(sample.celsius, status)

        val shouldPersist = lastSavedStatus == null ||
                status != lastSavedStatus ||
                status.isAlert ||
                batteryChangedSinceLastSave ||
                sample.receivedAt - lastSavedAt >= MIN_PERSIST_INTERVAL_MS

        if (!shouldPersist) return

        val reading = TemperatureReading(
            temperatureCelsius = sample.celsius,
            timestamp = sample.receivedAt,
            isAlertTriggered = status.isAlert,
            batteryLevel = latestBatteryPercent
        )

        temperatureRepository.saveReading(reading).onSuccess {
            lastSavedAt = sample.receivedAt
            lastSavedStatus = status
            batteryChangedSinceLastSave = false
        }
    }

    // ------------------------------------------------------------------
    //  Ubicación del estuche
    // ------------------------------------------------------------------
    /**
     * Guarda las posiciones del GPS del estuche para que el mapa tenga una
     * "última ubicación conocida" cuando se pierda el enlace. Las tramas
     * "sin señal" no se guardan: no son una posición.
     */
    private suspend fun collectCaseLocation() {
        deviceRepository.caseLocation.collect { state ->
            val reported = state as? CaseLocationState.Reported ?: return@collect
            val fix = reported.reading as? CaseGpsReading.Fix ?: return@collect
            onLocationFix(fix, reported.receivedAt)
        }
    }

    private suspend fun onLocationFix(fix: CaseGpsReading.Fix, receivedAt: Long) {
        val shouldPersist = shouldSaveLocation(
            lastSaved = lastSavedLocation,
            latitude = fix.latitude,
            longitude = fix.longitude,
            receivedAt = receivedAt
        )
        if (!shouldPersist) return

        val location = CaseLocation(
            latitude = fix.latitude,
            longitude = fix.longitude,
            timestamp = receivedAt
        )
        // Si falla el guardado no se actualiza la referencia: se reintenta
        // con la siguiente posición.
        caseLocationRepository.saveLocation(location).onSuccess {
            lastSavedLocation = location
        }
    }

    // ------------------------------------------------------------------
    //  Conexión con periodo de gracia
    // ------------------------------------------------------------------
    private suspend fun watchConnection() {
        deviceRepository.connectionStatus
            .map { it == ConnectionStatus.CONNECTED }
            .distinctUntilChanged()
            .collectLatest { isConnected ->
                if (isConnected) {
                    alertPublisher.onConnectionRestored()
                } else {
                    delay(DISCONNECTION_GRACE_MS)
                    alertPublisher.onConnectionLost()
                }
            }
    }

    private suspend fun purgeExpiredReadings() {
        val cutoff = clock() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        temperatureRepository.purgeReadingsOlderThan(cutoff)
    }

    private suspend fun purgeExpiredLocations() {
        val cutoff = clock() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        caseLocationRepository.deleteOlderThan(cutoff)
    }

    private companion object {
        const val MIN_PERSIST_INTERVAL_MS = 10_000L
        const val RETENTION_DAYS = 30L
        const val DISCONNECTION_GRACE_MS = 30_000L
    }
}