package com.example.medicaseclinico.ui.screens.geofence

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.geo.GeoPoint
import com.example.medicaseclinico.domain.model.CaseGpsReading
import com.example.medicaseclinico.domain.model.CaseLocation
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.Geofence
import com.example.medicaseclinico.domain.model.GeofenceLimits
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.repository.CaseLocationRepository
import com.example.medicaseclinico.domain.repository.GeofenceRepository
import com.example.medicaseclinico.domain.usecase.LocateCaseInGeofencesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Prepara la pantalla "Configurar Geocerca": el formulario de la zona nueva
 * (nombre, radio y el centro que el usuario sitúa moviendo el mapa) y la
 * lista de zonas guardadas con su "Dentro" / "Fuera".
 *
 * La zona nueva se guarda en Room; el sincronizador la empieza a vigilar
 * solo, sin alertar por la posición que el estuche ya tenía al crearla.
 */
class GeofenceConfigViewModel(
    private val geofenceRepository: GeofenceRepository,
    private val caseDeviceRepository: CaseDeviceRepository,
    private val caseLocationRepository: CaseLocationRepository,
    private val locateCaseInGeofences: LocateCaseInGeofencesUseCase
) : ViewModel() {

    /** Lo que el usuario lleva rellenado de la zona nueva. */
    private data class Form(
        val name: String = "",
        val radiusMeters: Int = GeofenceLimits.DEFAULT_RADIUS_METERS,
        val center: GeoPoint? = null,
        val isSaving: Boolean = false,
        val message: GeofenceConfigMessage? = null
    )

    private val form = MutableStateFlow(Form())
    private val retryTrigger = MutableStateFlow(0)

    /**
     * Texto del campo "Nombre de la Zona". Es estado de Compose y no parte del
     * `StateFlow` porque un campo de texto alimentado por un flujo se
     * actualiza con retraso y pierde letras al escribir rápido.
     */
    var zoneName by mutableStateOf("")
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<GeofenceConfigUiState> =
        retryTrigger
            .flatMapLatest { observeScreen() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = GeofenceConfigUiState.Loading
            )

    fun onNameChange(name: String) {
        val limited = name.take(GeofenceLimits.MAX_NAME_LENGTH)
        zoneName = limited
        form.update { it.copy(name = limited) }
    }

    fun onRadiusChange(radiusMeters: Int) {
        val radius = radiusMeters.coerceIn(
            GeofenceLimits.MIN_RADIUS_METERS,
            GeofenceLimits.MAX_RADIUS_METERS
        )
        form.update { it.copy(radiusMeters = radius) }
    }

    /** El usuario dejó de mover el mapa: su centro es el centro de la zona nueva. */
    fun onCenterChanged(center: GeoPoint) {
        form.update { it.copy(center = center) }
    }

    fun save() {
        val current = form.value
        val center = current.center ?: return
        val name = current.name.trim()
        if (name.isEmpty() || current.isSaving) return

        form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val geofence = Geofence(
                name = name,
                latitude = center.latitude,
                longitude = center.longitude,
                radiusMeters = current.radiusMeters.toDouble()
            )
            geofenceRepository.saveGeofence(geofence)
                .onSuccess {
                    // Se vacía el nombre para la siguiente zona; el radio y el
                    // lugar se quedan como los dejó el usuario.
                    zoneName = ""
                    form.update {
                        it.copy(name = "", isSaving = false, message = GeofenceConfigMessage.SAVED)
                    }
                }
                .onFailure {
                    form.update {
                        it.copy(isSaving = false, message = GeofenceConfigMessage.SAVE_FAILED)
                    }
                }
        }
    }

    /** La pantalla ya mostró el aviso: no debe repetirse al girar el teléfono. */
    fun onMessageShown() {
        form.update { it.copy(message = null) }
    }

    /** Vuelve a consultar las zonas y las posiciones tras un error. */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

    private fun observeScreen(): Flow<GeofenceConfigUiState> =
        combine(
            form,
            caseDeviceRepository.caseLocation,
            caseLocationRepository.observeLastKnownLocation(),
            geofenceRepository.observeGeofences()
        ) { form, liveState, lastKnown, geofences ->
            buildUiState(form, liveState, lastKnown, geofences)
        }
            .onStart { emit(GeofenceConfigUiState.Loading) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(GeofenceConfigUiState.Error)
            }

    private fun buildUiState(
        form: Form,
        liveState: CaseLocationState,
        lastKnown: CaseLocation?,
        geofences: List<Geofence>
    ): GeofenceConfigUiState {
        val casePosition = casePosition(liveState, lastKnown)
        val insideById = casePosition
            ?.let { locateCaseInGeofences(geofences, it.latitude, it.longitude) }
            ?.associate { it.geofence.id to it.isInside }
            .orEmpty()

        return GeofenceConfigUiState.Success(
            radiusMeters = form.radiusMeters,
            hasCenter = form.center != null,
            canSave = form.name.isNotBlank() && form.center != null && !form.isSaving,
            isSaving = form.isSaving,
            mapStart = casePosition
                ?: geofences.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) },
            casePosition = casePosition,
            savedZones = geofences.map { geofence ->
                SavedZone(
                    id = geofence.id,
                    name = geofence.name,
                    latitude = geofence.latitude,
                    longitude = geofence.longitude,
                    radiusMeters = geofence.radiusMeters,
                    isInside = insideById[geofence.id]
                )
            },
            message = form.message
        )
    }

    /** Posición en vivo del estuche o, si no la hay, la última conocida. Nunca la del teléfono. */
    private fun casePosition(liveState: CaseLocationState, lastKnown: CaseLocation?): GeoPoint? {
        val liveFix = (liveState as? CaseLocationState.Reported)?.reading as? CaseGpsReading.Fix
        return when {
            liveFix != null -> GeoPoint(liveFix.latitude, liveFix.longitude)
            lastKnown != null -> GeoPoint(lastKnown.latitude, lastKnown.longitude)
            else -> null
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                GeofenceConfigViewModel(
                    geofenceRepository = application.container.geofenceRepository,
                    caseDeviceRepository = application.container.caseDeviceRepository,
                    caseLocationRepository = application.container.caseLocationRepository,
                    locateCaseInGeofences = LocateCaseInGeofencesUseCase()
                )
            }
        }
    }
}
