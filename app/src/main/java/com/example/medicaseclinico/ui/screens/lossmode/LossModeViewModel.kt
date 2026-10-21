package com.example.medicaseclinico.ui.screens.lossmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.model.CaseCommandError
import com.example.medicaseclinico.domain.model.CaseCommandException
import com.example.medicaseclinico.domain.model.CaseControlState
import com.example.medicaseclinico.domain.model.CaseGpsReading
import com.example.medicaseclinico.domain.model.CaseLocation
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.GpsInterval
import com.example.medicaseclinico.domain.model.LossModeState
import com.example.medicaseclinico.domain.model.RelativeTime
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.repository.CaseLocationRepository
import com.example.medicaseclinico.domain.repository.LossModeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Prepara la pantalla "Modo Pérdida" y envía al estuche lo que el dueño pide.
 *
 * Reglas de honestidad de esta pantalla:
 *  - El seguro se muestra como lo informa el estuche; activar el modo no lo
 *    da por bloqueado.
 *  - La alarma y el GPS rápido solo se guardan como encendidos cuando el
 *    estuche aceptó la orden: sin enlace no se enciende nada "de palabra".
 *  - La posición es la del GPS del estuche (en vivo o la última guardada),
 *    nunca la del teléfono.
 *
 * @param clock y [tickIntervalMs] se inyectan para que los tests fijen el
 * tiempo relativo de la última posición.
 */
class LossModeViewModel(
    private val caseDeviceRepository: CaseDeviceRepository,
    private val caseLocationRepository: CaseLocationRepository,
    private val lossModeRepository: LossModeRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tickIntervalMs: Long = DEFAULT_TICK_INTERVAL_MS
) : ViewModel() {

    /** Lo que no viene de los repositorios. */
    private data class Local(
        val isBusy: Boolean = false,
        val offerLock: Boolean = false,
        val message: LossModeMessage? = null
    )

    /** Lo que llega del estuche y del historial, ya junto. */
    private data class CaseSnapshot(
        val control: CaseControlState,
        val liveLocation: CaseLocationState,
        val lastKnown: CaseLocation?,
        val now: Long
    )

    private val local = MutableStateFlow(Local())
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LossModeUiState> =
        retryTrigger
            .flatMapLatest { observeScreen() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = LossModeUiState.Loading
            )

    /**
     * Activa el modo. Si el estuche está conectado y se abre sin contraseña,
     * se ofrece activar el seguro: es lo primero que conviene hacer.
     */
    fun activate() = runBusy {
        val saved = lossModeRepository.update { it.activated(now = clock()) }
        if (saved.isFailure) {
            Local(message = LossModeMessage.NOT_SAVED)
        } else {
            val control = caseDeviceRepository.controlState.value
            Local(offerLock = control is CaseControlState.Ready && control.pinRequired == false)
        }
    }

    /** El dueño no quiso (o ya no hace falta) activar el seguro ahora. */
    fun onLockOfferDismissed() {
        local.update { it.copy(offerLock = false) }
    }

    /** El dueño pide activar el seguro desde el aviso del banner. */
    fun requestLock() {
        local.update { it.copy(offerLock = true) }
    }

    /** Activa el seguro del estuche. La contraseña se envía y se olvida. */
    fun enableLock(currentPin: String) = runBusy {
        val result = caseDeviceRepository.setPinRequired(required = true, currentPin = currentPin)
        Local(message = result.messageOr(LossModeMessage.LOCK_ENABLED))
    }

    fun setAlarm(on: Boolean) = runBusy {
        val result = caseDeviceRepository.setAlarm(on)
        if (result.isFailure) return@runBusy Local(message = result.messageOr(null))
        val saved = lossModeRepository.update { it.withAlarm(on) }
        Local(message = if (saved.isFailure) LossModeMessage.NOT_SAVED else null)
    }

    fun setHighFrequencyGps(enabled: Boolean) = runBusy {
        val seconds = if (enabled) GpsInterval.LOSS_MODE_SECONDS else GpsInterval.DEFAULT_SECONDS
        val result = caseDeviceRepository.setGpsIntervalSeconds(seconds)
        if (result.isFailure) return@runBusy Local(message = result.messageOr(null))
        val saved = lossModeRepository.update { it.withHighFrequencyGps(enabled) }
        Local(message = if (saved.isFailure) LossModeMessage.NOT_SAVED else null)
    }

    /**
     * Desactiva el modo aunque el estuche no esté al alcance: lo que decide
     * el dueño no puede depender del Bluetooth. Si había alarma o GPS rápido
     * y el estuche no recibe la orden de apagarlos, se avisa.
     */
    fun deactivate() = runBusy {
        val mode = currentMode() ?: return@runBusy Local(message = LossModeMessage.NOT_SAVED)
        var caseUpdated = true
        if (mode.alarmRequested) {
            caseUpdated = caseDeviceRepository.setAlarm(false).isSuccess && caseUpdated
        }
        if (mode.highFrequencyGpsRequested) {
            caseUpdated = caseDeviceRepository
                .setGpsIntervalSeconds(GpsInterval.DEFAULT_SECONDS).isSuccess && caseUpdated
        }
        val saved = lossModeRepository.update { it.deactivated() }
        Local(
            message = when {
                saved.isFailure -> LossModeMessage.NOT_SAVED
                !caseUpdated -> LossModeMessage.DEACTIVATED_CASE_NOT_UPDATED
                else -> null
            }
        )
    }

    /** La pantalla ya mostró el aviso: no debe repetirse al girar el teléfono. */
    fun onMessageShown() {
        local.update { it.copy(message = null) }
    }

    /** Vuelve a leer el estado guardado tras un error. */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

    /** El estado guardado ahora, o null si no se puede leer. */
    private suspend fun currentMode(): LossModeState? =
        try {
            lossModeRepository.state.first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /** Ejecuta una acción de una en una; su resultado sustituye al estado local. */
    private fun runBusy(action: suspend () -> Local) {
        if (local.value.isBusy) return
        local.update { it.copy(isBusy = true, offerLock = false) }
        viewModelScope.launch {
            local.value = action()
        }
    }

    private fun observeScreen(): Flow<LossModeUiState> =
        combine(lossModeRepository.state, observeCase(), local) { mode, case, local ->
            buildUiState(mode, case, local)
        }
            .onStart { emit(LossModeUiState.Loading) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(LossModeUiState.Error)
            }

    private fun observeCase(): Flow<CaseSnapshot> =
        combine(
            caseDeviceRepository.controlState,
            caseDeviceRepository.caseLocation,
            caseLocationRepository.observeLastKnownLocation(),
            currentTime()
        ) { control, liveLocation, lastKnown, now ->
            CaseSnapshot(control = control, liveLocation = liveLocation, lastKnown = lastKnown, now = now)
        }

    private fun currentTime(): Flow<Long> = flow {
        while (true) {
            emit(clock())
            delay(tickIntervalMs)
        }
    }

    private fun buildUiState(
        mode: LossModeState,
        case: CaseSnapshot,
        local: Local
    ): LossModeUiState {
        if (!mode.isActive) {
            return LossModeUiState.Inactive(isBusy = local.isBusy, message = local.message)
        }
        return LossModeUiState.Success(
            lock = case.control.toLockStatus(),
            position = positionOf(case),
            controls = case.control.toAvailability(),
            alarmOn = mode.alarmRequested,
            highFrequencyGps = mode.highFrequencyGpsRequested,
            gpsIntervalSeconds = if (mode.highFrequencyGpsRequested) {
                GpsInterval.LOSS_MODE_SECONDS
            } else {
                GpsInterval.DEFAULT_SECONDS
            },
            offerLock = local.offerLock,
            isBusy = local.isBusy,
            message = local.message
        )
    }

    private fun positionOf(case: CaseSnapshot): LossPosition? {
        val live = case.liveLocation
        val liveFix = (live as? CaseLocationState.Reported)?.reading as? CaseGpsReading.Fix
        if (live is CaseLocationState.Reported && liveFix != null) {
            return LossPosition(
                latitude = liveFix.latitude,
                longitude = liveFix.longitude,
                isLive = true,
                elapsed = RelativeTime.between(timestamp = live.receivedAt, now = case.now)
            )
        }
        val lastKnown = case.lastKnown ?: return null
        return LossPosition(
            latitude = lastKnown.latitude,
            longitude = lastKnown.longitude,
            isLive = false,
            elapsed = RelativeTime.between(timestamp = lastKnown.timestamp, now = case.now)
        )
    }

    private fun CaseControlState.toLockStatus(): LossLockStatus = when (this) {
        CaseControlState.Unavailable -> LossLockStatus.UNKNOWN
        CaseControlState.NotSupported -> LossLockStatus.NOT_SUPPORTED
        is CaseControlState.Ready -> when (pinRequired) {
            true -> LossLockStatus.LOCKED
            false -> LossLockStatus.UNLOCKED
            null -> LossLockStatus.UNKNOWN
        }
    }

    private fun CaseControlState.toAvailability(): CaseControlsAvailability = when (this) {
        CaseControlState.Unavailable -> CaseControlsAvailability.DISCONNECTED
        CaseControlState.NotSupported -> CaseControlsAvailability.NOT_SUPPORTED
        is CaseControlState.Ready -> CaseControlsAvailability.READY
    }

    /** El aviso de un fallo del estuche, o [success] si la orden salió bien. */
    private fun Result<Unit>.messageOr(success: LossModeMessage?): LossModeMessage? = fold(
        onSuccess = { success },
        onFailure = { failure ->
            when ((failure as? CaseCommandException)?.error ?: CaseCommandError.FAILED) {
                CaseCommandError.NOT_CONNECTED -> LossModeMessage.CASE_NOT_CONNECTED
                CaseCommandError.NOT_SUPPORTED -> LossModeMessage.CASE_NOT_SUPPORTED
                CaseCommandError.PAIRING_REQUIRED -> LossModeMessage.PAIRING_REQUIRED
                CaseCommandError.WRONG_PIN -> LossModeMessage.WRONG_PIN
                CaseCommandError.LOCKED_OUT -> LossModeMessage.CASE_LOCKED_OUT
                CaseCommandError.INVALID_VALUE -> LossModeMessage.INVALID_PIN
                // La alarma y el GPS no llevan PIN: nunca se bloquean por falta de TLS.
                CaseCommandError.SECURE_CONNECTION_REQUIRED,
                CaseCommandError.FAILED -> LossModeMessage.CASE_FAILED
            }
        }
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** El tiempo relativo cambia como mucho una vez por minuto. */
        const val DEFAULT_TICK_INTERVAL_MS = 60_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                LossModeViewModel(
                    caseDeviceRepository = application.container.caseDeviceRepository,
                    caseLocationRepository = application.container.caseLocationRepository,
                    lossModeRepository = application.container.lossModeRepository
                )
            }
        }
    }
}
