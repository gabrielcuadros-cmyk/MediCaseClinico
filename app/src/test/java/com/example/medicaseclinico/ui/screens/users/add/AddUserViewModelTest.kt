package com.example.medicaseclinico.ui.screens.users.add

import com.example.medicaseclinico.domain.model.AccessStatus
import com.example.medicaseclinico.domain.model.AuthUser
import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.UserPermission
import com.example.medicaseclinico.domain.model.UserPermissions
import com.example.medicaseclinico.domain.model.UserRole
import com.example.medicaseclinico.testing.FakeAuthRepository
import com.example.medicaseclinico.testing.FakeAuthorizedUsersRepository
import com.example.medicaseclinico.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddUserViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val owner = AuthUser(uid = "owner", displayName = "Alejandro Díaz", email = "ale@ejemplo.com")

    private val existing = AuthorizedUser(
        id = "carlos",
        name = "Carlos Ruiz",
        email = "carlos@ejemplo.com",
        phone = "+51912345678",
        role = UserRole.CAREGIVER
    )

    private fun viewModel(
        repository: FakeAuthorizedUsersRepository = FakeAuthorizedUsersRepository(),
        user: AuthUser? = owner
    ) = AddUserViewModel(
        authRepository = FakeAuthRepository(user),
        authorizedUsersRepository = repository
    )

    private fun AddUserViewModel.fill(
        name: String = " Laura Gómez ",
        email: String = " laura@ejemplo.com ",
        phone: String = "+51 987 654 321"
    ) {
        onNameChange(name)
        onEmailChange(email)
        onPhoneChange(phone)
    }

    @Test
    fun `el formulario abre como cuidador con los permisos de fabrica de ese rol`() {
        val state = viewModel().uiState.value

        assertEquals(UserRole.CAREGIVER, state.role)
        assertEquals(UserPermissions.defaultsFor(UserRole.CAREGIVER), state.permissions)
    }

    @Test
    fun `con el formulario vacio se marcan los tres campos y no se guarda nada`() = runTest {
        val repository = FakeAuthorizedUsersRepository()
        val viewModel = viewModel(repository)

        viewModel.sendInvitation()

        val state = viewModel.uiState.value
        assertEquals(AddUserFieldError.NAME_REQUIRED, state.nameError)
        assertEquals(AddUserFieldError.EMAIL_REQUIRED, state.emailError)
        assertEquals(AddUserFieldError.PHONE_REQUIRED, state.phoneError)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `un correo o un telefono mal escritos se rechazan y corregirlos quita el error`() = runTest {
        val viewModel = viewModel()
        viewModel.fill(email = "laura@", phone = "abc")

        viewModel.sendInvitation()
        assertEquals(AddUserFieldError.EMAIL_INVALID, viewModel.uiState.value.emailError)
        assertEquals(AddUserFieldError.PHONE_INVALID, viewModel.uiState.value.phoneError)

        viewModel.onEmailChange("laura@ejemplo.com")
        assertNull(viewModel.uiState.value.emailError)
        assertEquals(AddUserFieldError.PHONE_INVALID, viewModel.uiState.value.phoneError)
    }

    @Test
    fun `una invitacion valida guarda a la persona como pendiente y pide compartirla`() = runTest {
        val repository = FakeAuthorizedUsersRepository()
        val viewModel = viewModel(repository)
        viewModel.fill()

        viewModel.sendInvitation()

        val saved = repository.saved.single()
        assertEquals("Laura Gómez", saved.name)
        assertEquals("laura@ejemplo.com", saved.email)
        assertEquals("+51987654321", saved.phone)
        assertEquals(UserRole.CAREGIVER, saved.role)
        assertEquals(AccessStatus.INVITATION_PENDING, saved.status)
        assertEquals(UserPermissions.defaultsFor(UserRole.CAREGIVER), saved.permissions)

        assertEquals(
            PendingInvitation(name = "Laura Gómez", role = UserRole.CAREGIVER),
            viewModel.uiState.value.invitation
        )
        assertFalse(viewModel.uiState.value.isSubmitting)

        viewModel.onInvitationHandled()
        assertNull(viewModel.uiState.value.invitation)
    }

    @Test
    fun `cambiar de rol vuelve a los permisos de ese rol y se guardan los ajustes hechos despues`() = runTest {
        val repository = FakeAuthorizedUsersRepository()
        val viewModel = viewModel(repository)
        viewModel.fill()

        viewModel.onPermissionToggled(UserPermission.VIEW_ACCESS_LOGS, granted = true)
        viewModel.onRoleSelected(UserRole.SECONDARY_ADMIN)
        assertEquals(UserPermissions.ALL, viewModel.uiState.value.permissions)

        viewModel.onPermissionToggled(UserPermission.MANAGE_USERS, granted = false)
        viewModel.sendInvitation()

        val saved = repository.saved.single()
        assertEquals(UserRole.SECONDARY_ADMIN, saved.role)
        assertEquals(UserPermissions.ALL.copy(manageUsers = false), saved.permissions)
    }

    @Test
    fun `volver a tocar el mismo rol no borra los permisos ajustados`() {
        val viewModel = viewModel()

        viewModel.onPermissionToggled(UserPermission.VIEW_ACCESS_LOGS, granted = true)
        viewModel.onRoleSelected(UserRole.CAREGIVER)

        assertTrue(viewModel.uiState.value.permissions.viewAccessLogs)
    }

    @Test
    fun `no se puede invitar dos veces el mismo correo`() = runTest {
        val repository = FakeAuthorizedUsersRepository(listOf(existing))
        val viewModel = viewModel(repository)
        viewModel.fill(email = " Carlos@Ejemplo.com ")

        viewModel.sendInvitation()

        assertEquals(AddUserFieldError.EMAIL_ALREADY_INVITED, viewModel.uiState.value.emailError)
        assertTrue(repository.saved.isEmpty())
        assertNull(viewModel.uiState.value.invitation)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `sin sesion no se puede invitar`() = runTest {
        val repository = FakeAuthorizedUsersRepository()
        val viewModel = viewModel(repository, user = null)
        viewModel.fill()

        viewModel.sendInvitation()

        assertEquals(AddUserError.NO_SESSION, viewModel.uiState.value.error)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `si la nube no guarda se avisa y no se comparte ninguna invitacion`() = runTest {
        val repository = FakeAuthorizedUsersRepository().apply { failWrites = true }
        val viewModel = viewModel(repository)
        viewModel.fill()

        viewModel.sendInvitation()

        assertEquals(AddUserError.SAVE_FAILED, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.invitation)
    }

    @Test
    fun `si no se puede comprobar la lista no se guarda a ciegas`() = runTest {
        val repository = FakeAuthorizedUsersRepository().apply { failReads = true }
        val viewModel = viewModel(repository)
        viewModel.fill()

        viewModel.sendInvitation()

        assertEquals(AddUserError.SAVE_FAILED, viewModel.uiState.value.error)
        assertTrue(repository.saved.isEmpty())
    }
}
