package com.example.medicaseclinico.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.model.CaseGpsReading
import com.example.medicaseclinico.domain.model.CaseLocation
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.Geofence
import com.example.medicaseclinico.domain.model.RelativeTime
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.repository.CaseLocationRepository
import com.example.medicaseclinico.domain.repository.GeofenceRepository
import com.example.medicaseclinico.domain.usecase.LocateCaseInGeofencesUseCase
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

/**
 * Prepara la pantalla "Geolocalización".
 *
 * Une lo que el estuche dice ahora (BLE) con lo que quedó guardado (Room):
 * con posición en vivo se muestra esa; sin ella (sin señal GPS, sin enlace o
 * estuche sin GPS) se muestra la última conocida con su hora, y si tampoco
 * existe, el estado vacío. Nunca se usa la ubicación del teléfono.
 *
 * @param clock se inyecta para que los tests fijen la hora del tiempo relativo.
 * @param tickIntervalMs cada cuánto se recalcula "Hace X min" aunque no
 * lleguen posiciones nuevas.
 */
class MapViewModel(
    private val caseDeviceRepository: CaseDeviceRepository,
    private val caseLocationRepository: CaseLocationRepository,
    private val geofenceRepository: GeofenceRepository,
    private val locateCaseInGeofences: LocateCaseInGeofencesUseCase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tickIntervalMs: Long = DEFAULT_TICK_INTERVAL_MS
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MapUiState> =
        retryTrigger
            .flatMapLatest { observeMap() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = MapUiState.Loading
            )

    /** Vuelve a consultar las posiciones y las zonas tras un error. */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

    private fun observeMap(): Flow<MapUiState> =
        combine(
            caseDeviceRepository.caseLocation,
            caseLocationRepository.observeLastKnownLocation(),
            geofenceRepository.observeGeofences(),
            currentTime()
        ) { liveState, lastKnown, geofences, now ->
            buildUiState(liveState, lastKnown, geofences, now)
        }
            .onStart { emit(MapUiState.Loading) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(MapUiState.Error)
            }

    private fun currentTime(): Flow<Long> = flow {
        while (true) {
            emit(clock())
            delay(tickIntervalMs)
        }
    }

    private fun buildUiState(
        liveState: CaseLocationState,
        lastKnown: CaseLocation?,
        geofences: List<Geofence>,
        now: Long
    ): MapUiState {
        val liveStatus = liveState.toLiveStatus()
        val liveFix = (liveState as? CaseLocationState.Reported)?.reading as? CaseGpsReading.Fix

        val latitude: Double
        val longitude: Double
        val updatedAt: Long
        when {
            liveFix != null -> {
                latitude = liveFix.latitude
                longitude = liveFix.longitude
                updatedAt = (liveState as CaseLocationState.Reported).receivedAt
            }
            lastKnown != null -> {
                latitude = lastKnown.latitude
                longitude = lastKnown.longitude
                updatedAt = lastKnown.timestamp
            }
            else -> return MapUiState.Empty(liveStatus = liveStatus)
        }

        return MapUiState.Success(
            latitude = latitude,
            longitude = longitude,
            updatedAt = updatedAt,
            elapsed = RelativeTime.between(timestamp = updatedAt, now = now),
            liveStatus = liveStatus,
            geofences = locateCaseInGeofences(geofences, latitude, longitude).map { presence ->
                MapGeofence(
                    id = presence.geofence.id,
                    name = presence.geofence.name,
                    latitude = presence.geofence.latitude,
                    longitude = presence.geofence.longitude,
                    radiusMeters = presence.geofence.radiusMeters,
                    isInside = presence.isInside
                )
            }
        )
    }

    private fun CaseLocationState.toLiveStatus(): CaseLiveStatus = when (this) {
        CaseLocationState.Unavailable -> CaseLiveStatus.NO_LINK
        CaseLocationState.NotSupported -> CaseLiveStatus.GPS_NOT_SUPPORTED
        is CaseLocationState.Reported -> when (reading) {
            is CaseGpsReading.Fix -> CaseLiveStatus.LIVE
            CaseGpsReading.NoSignal -> CaseLiveStatus.NO_GPS_SIGNAL
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** El tiempo relativo cambia como mucho una vez por minuto. */
        const val DEFAULT_TICK_INTERVAL_MS = 60_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                MapViewModel(
                    caseDeviceRepository = application.container.caseDeviceRepository,
                    caseLocationRepository = application.container.caseLocationRepository,
                    geofenceRepository = application.container.geofenceRepository,
                    locateCaseInGeofences = LocateCaseInGeofencesUseCase()
                )
            }
        }
    }
}
