package com.example.medicaseclinico.ui.screens.dashboard

import com.example.medicaseclinico.domain.model.AlertEvent
import com.example.medicaseclinico.domain.model.AlertType
import com.example.medicaseclinico.domain.model.AuthUser
import com.example.medicaseclinico.domain.model.CaseControlState
import com.example.medicaseclinico.domain.model.CaseGpsReading
import com.example.medicaseclinico.domain.model.CaseLocation
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.DeviceIssue
import com.example.medicaseclinico.domain.model.LockState
import com.example.medicaseclinico.domain.model.LossModeState
import com.example.medicaseclinico.domain.model.RelativeTime
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.testing.FakeAlertEventRepository
import com.example.medicaseclinico.testing.FakeAuthRepository
import com.example.medicaseclinico.testing.FakeCaseDeviceRepository
import com.example.medicaseclinico.testing.FakeCaseLocationRepository
import com.example.medicaseclinico.testing.FakeLossModeRepository
import com.example.medicaseclinico.testing.FakeTemperatureRepository
import com.example.medicaseclinico.testing.MainDispatcherRule
import com.example.medicaseclinico.testing.keepObserved
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = 10 * MINUTE_MS

    private val temperature = FakeTemperatureRepository()
    private val device = FakeCaseDeviceRepository()
    private val locations = FakeCaseLocationRepository()
    private val alerts = FakeAlertEventRepository()
    private val auth = FakeAuthRepository()
    private val lossMode = FakeLossModeRepository()

    private fun reading(celsius: Float, minutesAgo: Int = 2) = TemperatureReading(
        temperatureCelsius = celsius,
        timestamp = now - minutesAgo * MINUTE_MS,
        isAlertTriggered = false
    )

    private fun TestScope.viewModel(): DashboardViewModel {
        val viewModel = DashboardViewModel(
            temperatureRepository = temperature,
            deviceRepository = device,
            caseLocationRepository = locations,
            alertEventRepository = alerts,
            authRepository = auth,
            lossModeRepository = lossMode,
            clock = { now }
        )
        keepObserved(viewModel.uiState)
        keepObserved(viewModel.userName)
        keepObserved(viewModel.lossModeActive)
        return viewModel
    }

    private fun DashboardViewModel.success() = uiState.value as DashboardUiState.Success

    @Test
    fun `sin lecturas guardadas el inicio esta vacio y no inventa una temperatura`() = runTest {
        val viewModel = viewModel()

        assertEquals(
            DashboardUiState.Empty(connectionStatus = ConnectionStatus.DISCONNECTED),
            viewModel.uiState.value
        )
    }

    @Test
    fun `muestra la ultima lectura clasificada y cuanto hace de ella`() = runTest {
        temperature.latest.value = reading(4.2f, minutesAgo = 2)
        temperature.batteryLevel.value = 78
        device.connectionStatus.value = ConnectionStatus.CONNECTED

        val state = viewModel().success()

        assertEquals(4.2f, state.currentTemperature)
        assertEquals(TemperatureStatus.SAFE, state.temperatureStatus)
        assertEquals(78, state.batteryLevel)
        assertFalse(state.isBatteryLow)
        assertEquals(RelativeTime.Minutes(2), state.lastReadingElapsed)
        assertTrue(state.isLiveData)
    }

    @Test
    fun `una temperatura fuera de rango se marca critica`() = runTest {
        temperature.latest.value = reading(9.0f)

        assertEquals(TemperatureStatus.CRITICAL, viewModel().success().temperatureStatus)
    }

    @Test
    fun `sin enlace la lectura mostrada no se da por actual`() = runTest {
        temperature.latest.value = reading(4.2f)

        assertFalse(viewModel().success().isLiveData)
    }

    @Test
    fun `sin nivel de bateria no se muestra ninguno y por debajo del umbral se avisa`() = runTest {
        temperature.latest.value = reading(4.2f)
        val viewModel = viewModel()
        assertNull(viewModel.success().batteryLevel)
        assertFalse(viewModel.success().isBatteryLow)

        temperature.batteryLevel.value = 15

        assertEquals(15, viewModel.success().batteryLevel)
        assertTrue(viewModel.success().isBatteryLow)
    }

    @Test
    fun `el cierre solo se muestra cuando el estuche lo informa`() = runTest {
        temperature.latest.value = reading(4.2f)
        val viewModel = viewModel()
        assertEquals(CaseLockStatus.UNKNOWN, viewModel.success().lockStatus)

        device.controlState.value = CaseControlState.NotSupported
        assertEquals(CaseLockStatus.NOT_SUPPORTED, viewModel.success().lockStatus)

        device.controlState.value = CaseControlState.Ready()
        assertEquals(CaseLockStatus.UNKNOWN, viewModel.success().lockStatus)

        device.controlState.value = CaseControlState.Ready(lockState = LockState.LOCKED)
        assertEquals(CaseLockStatus.LOCKED, viewModel.success().lockStatus)

        device.controlState.value = CaseControlState.Ready(lockState = LockState.OPEN)
        assertEquals(CaseLockStatus.OPEN, viewModel.success().lockStatus)
    }

    @Test
    fun `las secciones rapidas cuentan las alertas sin ver y el estado real del seguro`() = runTest {
        temperature.latest.value = reading(4.2f)
        alerts.events.value = listOf(
            AlertEvent(id = 1, type = AlertType.TEMPERATURE, timestamp = now, isRead = false),
            AlertEvent(id = 2, type = AlertType.SECURITY, timestamp = now, isRead = true),
            AlertEvent(id = 3, type = AlertType.SECURITY, timestamp = now, isRead = false)
        )
        val viewModel = viewModel()

        assertEquals(2, viewModel.success().quickSections.unreadAlerts)
        assertEquals(PinLockStatus.UNKNOWN, viewModel.success().quickSections.pinLock)

        device.controlState.value = CaseControlState.Ready(pinRequired = true)
        assertEquals(PinLockStatus.ENABLED, viewModel.success().quickSections.pinLock)

        device.controlState.value = CaseControlState.Ready(pinRequired = false)
        assertEquals(PinLockStatus.DISABLED, viewModel.success().quickSections.pinLock)
    }

    @Test
    fun `la ubicacion rapida usa la posicion en vivo y si no la ultima guardada`() = runTest {
        temperature.latest.value = reading(4.2f)
        val viewModel = viewModel()
        assertNull(viewModel.success().quickSections.lastLocationElapsed)

        locations.lastKnown.value = CaseLocation(
            latitude = -16.3989,
            longitude = -71.5369,
            timestamp = now - 5 * MINUTE_MS
        )
        assertEquals(RelativeTime.Minutes(5), viewModel.success().quickSections.lastLocationElapsed)

        device.caseLocation.value = CaseLocationState.Reported(
            reading = CaseGpsReading.Fix(latitude = -16.3989, longitude = -71.5369),
            receivedAt = now
        )
        assertEquals(RelativeTime.JustNow, viewModel.success().quickSections.lastLocationElapsed)

        // Con enlace pero sin señal GPS se vuelve a la última guardada.
        device.caseLocation.value = CaseLocationState.Reported(
            reading = CaseGpsReading.NoSignal,
            receivedAt = now
        )
        assertEquals(RelativeTime.Minutes(5), viewModel.success().quickSections.lastLocationElapsed)
    }

    @Test
    fun `un fallo inesperado al leer se muestra como error con reintento`() = runTest {
        temperature.readError = IllegalStateException("base de datos dañada")
        val viewModel = viewModel()

        val error = viewModel.uiState.value as DashboardUiState.Error
        assertEquals(ErrorReason.UNKNOWN, error.reason)
        assertTrue(error.canRetry)

        temperature.readError = null
        temperature.latest.value = reading(4.2f)
        viewModel.retry()

        assertEquals(4.2f, viewModel.success().currentTemperature)
        assertEquals(1, device.monitoringStarts)
    }

    @Test
    fun `un fallo pasajero se reintenta solo antes de mostrar el error`() = runTest {
        temperature.readError = IOException("sin red")
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value is DashboardUiState.Loading)

        // Tres reintentos con espera creciente: 1 s, 2 s y 4 s.
        advanceTimeBy(8_000L)

        assertEquals(ErrorReason.NETWORK, (viewModel.uiState.value as DashboardUiState.Error).reason)
    }

    @Test
    fun `el saludo usa el nombre real y sin sesion no saluda`() = runTest {
        val viewModel = viewModel()
        assertNull(viewModel.userName.value)

        auth.currentUser.value = AuthUser(uid = "u1", displayName = "Alejandro", email = "a@ejemplo.com")

        assertEquals("Alejandro", viewModel.userName.value)
    }

    @Test
    fun `el aviso de modo perdida sigue al estado guardado`() = runTest {
        val viewModel = viewModel()
        assertFalse(viewModel.lossModeActive.value)

        lossMode.stored.value = LossModeState().activated(now = now)
        assertTrue(viewModel.lossModeActive.value)

        lossMode.stored.value = LossModeState()
        assertFalse(viewModel.lossModeActive.value)
    }

    @Test
    fun `al volver a la pantalla solo se reintenta lo que el usuario puede haber resuelto`() = runTest {
        val viewModel = viewModel()

        device.deviceIssue.value = DeviceIssue.DEVICE_NOT_FOUND
        viewModel.onScreenResumed()
        assertEquals(0, device.monitoringStarts)

        device.deviceIssue.value = DeviceIssue.BLUETOOTH_DISABLED
        viewModel.onScreenResumed()
        assertEquals(1, device.monitoringStarts)
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
