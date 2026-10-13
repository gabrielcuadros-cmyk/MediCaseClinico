package com.example.medicaseclinico.ui.screens.users.permissions

import com.example.medicaseclinico.domain.model.AuthUser
import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.UserPermission
import com.example.medicaseclinico.domain.model.UserPermissions
import com.example.medicaseclinico.domain.model.UserRole
import com.example.medicaseclinico.domain.repository.AuthRepository
import com.example.medicaseclinico.domain.repository.AuthorizedUsersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserPermissionsViewModelTest {

    private class FakeAuthRepository(user: AuthUser?) : AuthRepository {
        override val currentUser = MutableStateFlow(user)
        override suspend fun signIn(email: String, password: String) = notUsed<AuthUser>()
        override suspend fun register(name: String, email: String, password: String) = notUsed<AuthUser>()
        override suspend fun sendPasswordReset(email: String) = notUsed<Unit>()
        override suspend fun signOut() = notUsed<Unit>()

        private fun <T> notUsed(): Result<T> = Result.failure(UnsupportedOperationException())
    }

    /**
     * @param echoWrites si las escrituras vuelven por el `Flow`, como hace
     * Firestore. En false simula un servidor que aún no ha respondido.
     */
    private class FakeUsersRepository(
        initial: List<AuthorizedUser>,
        private val echoWrites: Boolean = true
    ) : AuthorizedUsersRepository {
        val users = MutableStateFlow(initial)
        val saved = mutableListOf<AuthorizedUser>()
        val deleted = mutableListOf<String>()
        var failWrites = false
        var failReads = false

        override fun observeUsers(ownerId: String): Flow<List<AuthorizedUser>> =
            if (failReads) flow { throw IllegalStateException("sin permiso") } else users

        override suspend fun saveUser(ownerId: String, user: AuthorizedUser): Result<String> {
            if (failWrites) return Result.failure(IllegalStateException("sin red"))
            saved += user
            if (echoWrites) users.value = users.value.map { if (it.id == user.id) user else it }
            return Result.success(user.id)
        }

        override suspend fun deleteUser(ownerId: String, userId: String): Result<Unit> {
            if (failWrites) return Result.failure(IllegalStateException("sin red"))
            deleted += userId
            if (echoWrites) users.value = users.value.filterNot { it.id == userId }
            return Result.success(Unit)
        }
    }

    private val laura = AuthorizedUser(
        id = "laura",
        name = "Laura Gómez",
        email = "laura@ejemplo.com",
        phone = "+51987654321",
        role = UserRole.CAREGIVER
    )

    private val owner = AuthUser(uid = "owner", displayName = "Alejandro Díaz", email = "ale@ejemplo.com")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Crea el ViewModel y deja a alguien observando su estado, como haría la pantalla. */
    private fun TestScope.viewModel(
        repository: FakeUsersRepository,
        user: AuthUser? = owner,
        userId: String = laura.id
    ): UserPermissionsViewModel {
        val viewModel = UserPermissionsViewModel(
            userId = userId,
            authRepository = FakeAuthRepository(user),
            authorizedUsersRepository = repository
        )
        // Sin cola: el estado debe estar listo en cuanto se pide, como en Main.immediate.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        return viewModel
    }

    private fun UserPermissionsViewModel.success() = uiState.value as UserPermissionsUiState.Success

    private fun UserPermissionsUiState.Success.granted(): List<UserPermission> =
        permissions.filter { it.granted }.map { it.permission }

    @Test
    fun `muestra a la persona con los permisos que tiene guardados`() = runTest {
        val viewModel = viewModel(FakeUsersRepository(listOf(laura)))

        val state = viewModel.success()
        assertEquals("Laura Gómez", state.name)
        assertEquals("LG", state.initials)
        assertEquals(UserRole.CAREGIVER, state.role)
        assertEquals(UserPermission.entries, state.permissions.map { it.permission })
        assertEquals(
            listOf(
                UserPermission.MONITOR_TEMPERATURE,
                UserPermission.VIEW_LOCATION,
                UserPermission.RECEIVE_CRITICAL_ALERTS
            ),
            state.granted()
        )
    }

    @Test
    fun `cambiar un permiso guarda a la persona con solo ese permiso cambiado`() = runTest {
        val repository = FakeUsersRepository(listOf(laura))
        val viewModel = viewModel(repository)

        viewModel.setPermission(UserPermission.EXPORT_REPORTS, granted = true)

        assertEquals(
            laura.copy(permissions = laura.permissions.copy(exportReports = true)),
            repository.saved.single()
        )
        assertTrue(UserPermission.EXPORT_REPORTS in viewModel.success().granted())
    }

    @Test
    fun `dejar un permiso como estaba no escribe nada`() = runTest {
        val repository = FakeUsersRepository(listOf(laura))
        val viewModel = viewModel(repository)

        viewModel.setPermission(UserPermission.VIEW_LOCATION, granted = true)

        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `dos cambios seguidos no se pisan aunque el servidor no haya respondido`() = runTest {
        val repository = FakeUsersRepository(listOf(laura), echoWrites = false)
        val viewModel = viewModel(repository)

        viewModel.setPermission(UserPermission.EXPORT_REPORTS, granted = true)
        viewModel.setPermission(UserPermission.VIEW_LOCATION, granted = false)

        assertEquals(
            UserPermissions(monitorTemperature = true, receiveCriticalAlerts = true, exportReports = true),
            repository.saved.last().permissions
        )
    }

    @Test
    fun `si no se puede guardar un permiso se avisa una sola vez`() = runTest {
        val repository = FakeUsersRepository(listOf(laura)).apply { failWrites = true }
        val viewModel = viewModel(repository)

        viewModel.setPermission(UserPermission.EXPORT_REPORTS, granted = true)
        assertTrue(viewModel.success().saveFailed)

        viewModel.onSaveErrorShown()
        assertFalse(viewModel.success().saveFailed)
    }

    @Test
    fun `revocar el acceso borra a la persona y cierra la pantalla`() = runTest {
        val repository = FakeUsersRepository(listOf(laura))
        val viewModel = viewModel(repository)

        viewModel.revokeAccess()

        assertEquals(listOf("laura"), repository.deleted)
        assertEquals(UserPermissionsUiState.Revoked, viewModel.uiState.value)
    }

    @Test
    fun `si no se puede revocar la persona sigue en pantalla y se avisa`() = runTest {
        val repository = FakeUsersRepository(listOf(laura)).apply { failWrites = true }
        val viewModel = viewModel(repository)

        viewModel.revokeAccess()

        val state = viewModel.success()
        assertTrue(state.revokeFailed)
        assertFalse(state.isRevoking)
        assertTrue(repository.deleted.isEmpty())

        viewModel.onRevokeErrorShown()
        assertFalse(viewModel.success().revokeFailed)
    }

    @Test
    fun `tras revocar ya no se guardan cambios de permisos`() = runTest {
        val repository = FakeUsersRepository(listOf(laura), echoWrites = false)
        val viewModel = viewModel(repository)

        viewModel.revokeAccess()
        viewModel.setPermission(UserPermission.EXPORT_REPORTS, granted = true)

        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `una persona que ya no esta en la lista se muestra como no encontrada`() = runTest {
        val viewModel = viewModel(FakeUsersRepository(listOf(laura)), userId = "otra")

        assertEquals(UserPermissionsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `sin sesion no hay persona que mostrar`() = runTest {
        val viewModel = viewModel(FakeUsersRepository(listOf(laura)), user = null)

        assertEquals(UserPermissionsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `si la lista no se puede leer se ofrece reintentar y al reintentar se recupera`() = runTest {
        val repository = FakeUsersRepository(listOf(laura)).apply { failReads = true }
        val viewModel = viewModel(repository)
        assertEquals(UserPermissionsUiState.Error, viewModel.uiState.value)

        repository.failReads = false
        viewModel.retry()

        assertEquals("Laura Gómez", viewModel.success().name)
    }
}
