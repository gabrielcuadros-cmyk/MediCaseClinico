package com.example.medicaseclinico.ui.screens.security

import com.example.medicaseclinico.domain.model.AppearanceMode
import com.example.medicaseclinico.domain.model.CaseControlState
import com.example.medicaseclinico.domain.repository.AppearanceRepository
import com.example.medicaseclinico.testing.FakeAuthRepository
import com.example.medicaseclinico.testing.FakeAuthorizedUsersRepository
import com.example.medicaseclinico.testing.FakeCaseDeviceRepository
import com.example.medicaseclinico.testing.MainDispatcherRule
import com.example.medicaseclinico.testing.keepObserved
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Cuándo deja la pantalla de Seguridad gestionar la contraseña del estuche. */
class SecurityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val device = FakeCaseDeviceRepository()

    private val appearance = object : AppearanceRepository {
        override val mode = MutableStateFlow(AppearanceMode.DEFAULT)
        override suspend fun setMode(mode: AppearanceMode): Result<Unit> = Result.success(Unit)
    }

    private fun TestScope.viewModel(): SecurityViewModel {
        val viewModel = SecurityViewModel(
            deviceRepository = device,
            authRepository = FakeAuthRepository(),
            authorizedUsersRepository = FakeAuthorizedUsersRepository(),
            appearanceRepository = appearance
        )
        keepObserved(viewModel.uiState)
        return viewModel
    }

    private fun SecurityViewModel.pin() = (uiState.value as SecurityUiState.Success).pin

    @Test
    fun `con enlace cifrado y estuche listo se puede cambiar la contrasena`() = runTest {
        device.controlState.value = CaseControlState.Ready(pinRequired = true)

        val pin = viewModel().pin()

        assertEquals(CasePinAvailability.READY, pin.availability)
        assertTrue(pin.canChange)
    }

    @Test
    fun `sin enlace cifrado los controles quedan desactivados aunque el estuche este listo`() = runTest {
        device.canSendPinSecurely = false
        device.controlState.value = CaseControlState.Ready(pinRequired = true)

        val pin = viewModel().pin()

        assertEquals(CasePinAvailability.SECURE_CONNECTION_REQUIRED, pin.availability)
        assertFalse(pin.canChange)
        // La posición del seguro es un dato real del estuche: se sigue mostrando.
        assertEquals(true, pin.pinRequired)
    }

    @Test
    fun `sin enlace cifrado el aviso de conexion segura va antes que el de estuche desconectado`() = runTest {
        device.canSendPinSecurely = false

        val pin = viewModel().pin()

        assertEquals(CasePinAvailability.SECURE_CONNECTION_REQUIRED, pin.availability)
        assertNull(pin.pinRequired)
    }
}
