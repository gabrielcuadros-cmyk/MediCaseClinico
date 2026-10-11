package com.example.medicaseclinico.data.sync

import com.example.medicaseclinico.domain.model.CaseTelemetry
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.repository.TemperatureRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Puente entre el enlace BLE en vivo y el historial persistente.
 *
 * Muestreo: se guarda una lectura cada MIN_PERSIST_INTERVAL_MS, pero
 * SIEMPRE de inmediato si cambia el estado clínico, si hay alerta o si cambió la batería.
 */
class CaseTelemetrySynchronizer(
    private val deviceRepository: CaseDeviceRepository,
    private val temperatureRepository: TemperatureRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var syncJob: Job? = null

    private var latestBatteryPercent: Int? = null
    private var batteryChangedSinceLastSave = false
    private var lastSavedAt: Long = 0L
    private var lastSavedStatus: TemperatureStatus? = null

    /** Inicia la sincronización. Idempotente. */
    fun start() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            purgeExpiredReadings()
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
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
    }

    private fun onBattery(battery: CaseTelemetry.Battery) {
        if (battery.levelPercent != latestBatteryPercent) {
            latestBatteryPercent = battery.levelPercent
            batteryChangedSinceLastSave = true
        }
    }

    private suspend fun onTemperature(sample: CaseTelemetry.Temperature) {
        val status = TemperatureStatus.fromCelsius(sample.celsius)
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

    private suspend fun purgeExpiredReadings() {
        val cutoff = clock() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        temperatureRepository.purgeReadingsOlderThan(cutoff)
    }

    private companion object {
        const val MIN_PERSIST_INTERVAL_MS = 10_000L
        const val RETENTION_DAYS = 30L
    }
}