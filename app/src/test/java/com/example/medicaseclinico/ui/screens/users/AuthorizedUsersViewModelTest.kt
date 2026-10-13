package com.example.medicaseclinico.ui.screens.users

import com.example.medicaseclinico.domain.model.AccessStatus
import com.example.medicaseclinico.domain.model.AuthUser
import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.UserRole
import com.example.medicaseclinico.domain.user.LastAccessDay
import com.example.medicaseclinico.testing.FakeAuthRepository
import com.example.medicaseclinico.testing.FakeAuthorizedUsersRepository
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
import java.time.LocalDateTime
import java.time.ZoneId

class AuthorizedUsersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val lima = ZoneId.of("America/Lima")
    private val now = at(day = 12, hour = 20)

    private fun at(day: Int, hour: Int): Long =
        LocalDateTime.of(2026, 10, day, hour, 0).atZone(lima).toInstant().toEpochMilli()

    private val owner = AuthUser(
        uid = "owner",
        displayName = "Alejandro Díaz",
        email = "ale@ejemplo.com",
        lastSignInAt = at(day = 12, hour = 8)
    )

    private val laura = AuthorizedUser(
        id = "laura",
        name = "Laura Gómez",
        email = "laura@ejemplo.com",
        phone = "+51987654321",
        role = UserRole.CAREGIVER,
        status = AccessStatus.INVITATION_PENDING
    )

    private val carlos = AuthorizedUser(
        id = "carlos",
        name = "Carlos Ruiz",
        email = "carlos@ejemplo.com",
        phone = "+51912345678",
        role = UserRole.SECONDARY_ADMIN,
        status = AccessStatus.ACTIVE,
        lastAccessAt = at(day = 11, hour = 18)
    )

    private fun TestScope.viewModel(
        repository: FakeAuthorizedUsersRepository,
        user: AuthUser? = owner
    ): AuthorizedUsersViewModel {
        val viewModel = AuthorizedUsersViewModel(
            authRepository = FakeAuthRepository(user),
            authorizedUsersRepository = repository,
            clock = { now },
            zone = { lima }
        )
        keepObserved(viewModel.uiState)
        return viewModel
    }

    private fun AuthorizedUsersViewModel.success() = uiState.value as AuthorizedUsersUiState.Success

    @Test
    fun `sin sesion no hay lista que mostrar`() = runTest {
        val viewModel = viewModel(FakeAuthorizedUsersRepository(listOf(laura)), user = null)

        assertEquals(AuthorizedUsersUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `el dueno va primero como principal y no se le puede quitar el acceso`() = runTest {
        val principal = viewModel(FakeAuthorizedUsersRepository(listOf(laura))).success().users.first()

        assertEquals("Alejandro Díaz", principal.name)
        assertEquals("AD", principal.initials)
        assertEquals(UserRole.PRINCIPAL, principal.role)
        assertFalse(principal.removable)
        assertFalse(principal.invitationPending)
        assertEquals(LastAccessDay.TODAY, principal.lastAccess?.day)
        assertEquals(owner.lastSignInAt, principal.lastAccess?.atMillis)
    }

    @Test
    fun `un dueno sin nombre se muestra con su correo y sin ultimo acceso si no se conoce`() = runTest {
        val unnamed = AuthUser(uid = "owner", displayName = null, email = "ale@ejemplo.com")

        val principal = viewModel(FakeAuthorizedUsersRepository(), user = unnamed).success().users.single()

        assertEquals("ale@ejemplo.com", principal.name)
        assertNull(principal.lastAccess)
    }

    @Test
    fun `los invitados muestran si su invitacion sigue pendiente o cuando accedieron`() = runTest {
        val users = viewModel(FakeAuthorizedUsersRepository(listOf(laura, carlos))).success().users

        val pending = users.first { it.id == "laura" }
        assertTrue(pending.invitationPending)
        assertTrue(pending.removable)
        assertNull(pending.lastAccess)

        val active = users.first { it.id == "carlos" }
        assertFalse(active.invitationPending)
        assertEquals(LastAccessDay.YESTERDAY, active.lastAccess?.day)
        assertEquals("CR", active.initials)
    }

    @Test
    fun `quitar el acceso borra a la persona y avisa una sola vez`() = runTest {
        val repository = FakeAuthorizedUsersRepository(listOf(laura, carlos))
        val viewModel = viewModel(repository)

        viewModel.removeUser("laura")

        assertEquals(listOf("laura"), repository.deleted)
        assertEquals(listOf("owner", "carlos"), viewModel.success().users.map { it.id })
        assertEquals(AuthorizedUsersMessage.USER_REMOVED, viewModel.success().message)
        assertFalse(viewModel.success().isBusy)

        viewModel.onMessageShown()
        assertNull(viewModel.success().message)
    }

    @Test
    fun `si no se puede quitar el acceso la persona sigue en la lista y se avisa`() = runTest {
        val repository = FakeAuthorizedUsersRepository(listOf(laura)).apply { failWrites = true }
        val viewModel = viewModel(repository)

        viewModel.removeUser("laura")

        assertEquals(AuthorizedUsersMessage.USER_REMOVE_FAILED, viewModel.success().message)
        assertEquals(listOf("owner", "laura"), viewModel.success().users.map { it.id })
    }

    @Test
    fun `si la lista no se puede leer se ofrece reintentar y al reintentar se recupera`() = runTest {
        val repository = FakeAuthorizedUsersRepository(listOf(laura)).apply { failReads = true }
        val viewModel = viewModel(repository)
        assertEquals(AuthorizedUsersUiState.Error, viewModel.uiState.value)

        repository.failReads = false
        viewModel.retry()

        assertEquals(2, viewModel.success().users.size)
    }
}
