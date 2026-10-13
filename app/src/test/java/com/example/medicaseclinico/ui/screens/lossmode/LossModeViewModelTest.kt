package com.example.medicaseclinico.ui.screens.lossmode

import com.example.medicaseclinico.domain.model.CaseCommandError
import com.example.medicaseclinico.domain.model.CaseControlState
import com.example.medicaseclinico.domain.model.CaseGpsReading
import com.example.medicaseclinico.domain.model.CaseLocation
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.GpsInterval
import com.example.medicaseclinico.domain.model.LossModeState
import com.example.medicaseclinico.domain.model.RelativeTime
import com.example.medicaseclinico.testing.FakeCaseDeviceRepository
import com.example.medicaseclinico.testing.FakeCaseLocationRepository
import com.example.medicaseclinico.testing.FakeLossModeRepository
import com.example.medicaseclinico.testing.MainDispatcherRule
import com.example.medicaseclinico.testing.keepObserved
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LossModeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = 60 * MINUTE_MS

    private val device = FakeCaseDeviceRepository()
    private val locations = FakeCaseLocationRepository()
    private val lossMode = FakeLossModeRepository()

    private fun TestScope.viewModel(): LossModeViewModel {
        val viewModel = LossModeViewModel(
            caseDeviceRepository = device,
            caseLocationRepository = locations,
            lossModeRepository = lossMode,
            clock = { now }
        )
        keepObserved(viewModel.uiState)
        return viewModel
    }

    private fun LossModeViewModel.success() = uiState.value as LossModeUiState.Success

    private fun activeMode() = LossModeState().activated(now = now)

    @Test
    fun `por defecto el modo esta desactivado`() = runTest {
        assertEquals(
            LossModeUiState.Inactive(isBusy = false, message = null),
            viewModel().uiState.value
        )
    }

    @Test
    fun `activar guarda el modo con su hora y sin estuche no afirma que este bloqueado`() = runTest {
        val viewModel = viewModel()

        viewModel.activate()

        assertEquals(LossModeState(isActive = true, activatedAt = now), lossMode.stored.value)
        val state = viewModel.success()
        assertEquals(LossLockStatus.UNKNOWN, state.lock)
        assertEquals(CaseControlsAvailability.DISCONNECTED, state.controls)
        assertFalse(state.offerLock)
        assertTrue(device.commands.isEmpty())
    }

    @Test
    fun `al activar con el seguro apagado se ofrece activarlo y hacerlo bloquea el estuche`() = runTest {
        device.controlState.value = CaseControlState.Ready(pinRequired = false)
        val viewModel = viewModel()

        viewModel.activate()
        assertTrue(viewModel.success().offerLock)
        assertEquals(LossLockStatus.UNLOCKED, viewModel.success().lock)

        viewModel.enableLock(currentPin = "1234")

        // Se anota la orden, nunca la contraseña.
        assertEquals(listOf("pinRequired=true"), device.commands)
        assertEquals(LossLockStatus.LOCKED, viewModel.success().lock)
        assertEquals(LossModeMessage.LOCK_ENABLED, viewModel.success().message)
        assertFalse(viewModel.success().offerLock)
    }

    @Test
    fun `con el seguro ya activo no se ofrece nada y rechazar la oferta la cierra`() = runTest {
        device.controlState.value = CaseControlState.Ready(pinRequired = true)
        val viewModel = viewModel()

        viewModel.activate()
        assertFalse(viewModel.success().offerLock)
        assertEquals(LossLockStatus.LOCKED, viewModel.success().lock)

        viewModel.requestLock()
        assertTrue(viewModel.success().offerLock)
        viewModel.onLockOfferDismissed()
        assertFalse(viewModel.success().offerLock)
    }

    @Test
    fun `una contrasena incorrecta se avisa y el estuche sigue sin seguro`() = runTest {
        lossMode.stored.value = activeMode()
        device.controlState.value = CaseControlState.Ready(pinRequired = false)
        device.commandError = CaseCommandError.WRONG_PIN
        val viewModel = viewModel()

        viewModel.enableLock(currentPin = "0000")

        assertEquals(LossModeMessage.WRONG_PIN, viewModel.success().message)
        assertEquals(LossLockStatus.UNLOCKED, viewModel.success().lock)

        viewModel.onMessageShown()
        assertNull(viewModel.success().message)
    }

    @Test
    fun `la alarma solo queda encendida si el estuche acepta la orden`() = runTest {
        lossMode.stored.value = activeMode()
        device.controlState.value = CaseControlState.Ready()
        val viewModel = viewModel()

        viewModel.setAlarm(true)
        assertEquals(listOf("alarm=true"), device.commands)
        assertTrue(viewModel.success().alarmOn)
        assertTrue(lossMode.stored.value.alarmRequested)

        device.commandError = CaseCommandError.NOT_CONNECTED
        viewModel.setAlarm(false)

        // El estuche no la apagó: no se muestra como apagada.
        assertTrue(viewModel.success().alarmOn)
        assertEquals(LossModeMessage.CASE_NOT_CONNECTED, viewModel.success().message)
    }

    @Test
    fun `el gps rapido usa los intervalos del protocolo`() = runTest {
        lossMode.stored.value = activeMode()
        device.controlState.value = CaseControlState.Ready()
        val viewModel = viewModel()
        assertEquals(GpsInterval.DEFAULT_SECONDS, viewModel.success().gpsIntervalSeconds)

        viewModel.setHighFrequencyGps(true)
        assertTrue(viewModel.success().highFrequencyGps)
        assertEquals(GpsInterval.LOSS_MODE_SECONDS, viewModel.success().gpsIntervalSeconds)

        viewModel.setHighFrequencyGps(false)
        assertEquals(
            listOf("gps=${GpsInterval.LOSS_MODE_SECONDS}", "gps=${GpsInterval.DEFAULT_SECONDS}"),
            device.commands
        )
        assertFalse(viewModel.success().highFrequencyGps)
    }

    @Test
    fun `la posicion es la del estuche en vivo o la ultima guardada y nunca una inventada`() = runTest {
        lossMode.stored.value = activeMode()
        val viewModel = viewModel()
        assertNull(viewModel.success().position)

        locations.lastKnown.value = CaseLocation(
            latitude = -16.3989,
            longitude = -71.5369,
            timestamp = now - 9 * MINUTE_MS
        )
        val lastKnown = viewModel.success().position
        assertEquals(false, lastKnown?.isLive)
        assertEquals(RelativeTime.Minutes(9), lastKnown?.elapsed)

        device.caseLocation.value = CaseLocationState.Reported(
            reading = CaseGpsReading.Fix(latitude = -16.4000, longitude = -71.5400),
            receivedAt = now
        )
        val live = viewModel.success().position
        assertEquals(true, live?.isLive)
        assertEquals(-16.4000, live?.latitude ?: 0.0, 0.0)
    }

    @Test
    fun `desactivar apaga la alarma y el gps rapido del estuche`() = runTest {
        lossMode.stored.value = activeMode().withAlarm(true).withHighFrequencyGps(true)
        device.controlState.value = CaseControlState.Ready()
        val viewModel = viewModel()

        viewModel.deactivate()

        assertEquals(listOf("alarm=false", "gps=${GpsInterval.DEFAULT_SECONDS}"), device.commands)
        assertEquals(LossModeState(), lossMode.stored.value)
        assertEquals(LossModeUiState.Inactive(isBusy = false, message = null), viewModel.uiState.value)
    }

    @Test
    fun `desactivar funciona sin estuche pero avisa de que la alarma puede seguir sonando`() = runTest {
        lossMode.stored.value = activeMode().withAlarm(true)
        device.commandError = CaseCommandError.NOT_CONNECTED
        val viewModel = viewModel()

        viewModel.deactivate()

        assertEquals(LossModeState(), lossMode.stored.value)
        assertEquals(
            LossModeUiState.Inactive(
                isBusy = false,
                message = LossModeMessage.DEACTIVATED_CASE_NOT_UPDATED
            ),
            viewModel.uiState.value
        )
    }

    @Test
    fun `desactivar sin ayudas encendidas no envia nada al estuche`() = runTest {
        lossMode.stored.value = activeMode()
        val viewModel = viewModel()

        viewModel.deactivate()

        assertTrue(device.commands.isEmpty())
        assertEquals(LossModeUiState.Inactive(isBusy = false, message = null), viewModel.uiState.value)
    }

    @Test
    fun `si no se puede guardar el modo no se da por activado`() = runTest {
        lossMode.failWrites = true
        val viewModel = viewModel()

        viewModel.activate()

        assertEquals(
            LossModeUiState.Inactive(isBusy = false, message = LossModeMessage.NOT_SAVED),
            viewModel.uiState.value
        )
    }

    @Test
    fun `si el estado guardado no se puede leer se ofrece reintentar`() = runTest {
        lossMode.failReads = true
        val viewModel = viewModel()
        assertEquals(LossModeUiState.Error, viewModel.uiState.value)

        lossMode.failReads = false
        viewModel.retry()

        assertEquals(LossModeUiState.Inactive(isBusy = false, message = null), viewModel.uiState.value)
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
