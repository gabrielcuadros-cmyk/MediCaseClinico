package com.example.medicaseclinico.ui.screens.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.model.AuthUser
import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.UserRole
import com.example.medicaseclinico.domain.repository.AuthRepository
import com.example.medicaseclinico.domain.repository.AuthorizedUsersRepository
import com.example.medicaseclinico.domain.user.initialsOf
import com.example.medicaseclinico.domain.user.lastAccessDayOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

/**
 * Prepara la pantalla "Usuarios Autorizados": el dueño de la cuenta y las
 * personas a las que dio acceso.
 *
 * El dueño no está guardado en la lista de la nube (es quien la posee), así
 * que su fila se arma con los datos reales de su sesión.
 *
 * @param clock y [zone] se inyectan para decidir "Hoy" / "Ayer" de forma
 * comprobable en tests.
 */
class AuthorizedUsersViewModel(
    private val authRepository: AuthRepository,
    private val authorizedUsersRepository: AuthorizedUsersRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) : ViewModel() {

    /** Lo que no viene de los repositorios: trabajo en curso y aviso pendiente. */
    private data class Local(
        val isBusy: Boolean = false,
        val message: AuthorizedUsersMessage? = null
    )

    /** El dueño con su lista, o null si no hay sesión. */
    private data class Owned(val owner: AuthUser, val users: List<AuthorizedUser>)

    private val local = MutableStateFlow(Local())
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AuthorizedUsersUiState> =
        retryTrigger
            .flatMapLatest { observeScreen() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AuthorizedUsersUiState.Loading
            )

    /** Quita el acceso a una persona. La pantalla ya pidió confirmación. */
    fun removeUser(userId: String) {
        if (local.value.isBusy) return
        local.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val ownerId = currentOwnerId()
            val result = if (ownerId == null) {
                Result.failure(IllegalStateException("Sin sesión"))
            } else {
                authorizedUsersRepository.deleteUser(ownerId = ownerId, userId = userId)
            }
            local.value = Local(
                message = if (result.isSuccess) {
                    AuthorizedUsersMessage.USER_REMOVED
                } else {
                    AuthorizedUsersMessage.USER_REMOVE_FAILED
                }
            )
        }
    }

    /** La pantalla ya mostró el aviso: no debe repetirse al girar el teléfono. */
    fun onMessageShown() {
        local.update { it.copy(message = null) }
    }

    /** Vuelve a leer la lista tras un error. */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

    private fun observeScreen(): Flow<AuthorizedUsersUiState> =
        combine(observeOwned(), local) { owned, local ->
            if (owned == null) {
                AuthorizedUsersUiState.Empty
            } else {
                AuthorizedUsersUiState.Success(
                    users = listOf(owned.owner.toItem()) + owned.users.map { it.toItem() },
                    isBusy = local.isBusy,
                    message = local.message
                )
            }
        }
            .onStart { emit(AuthorizedUsersUiState.Loading) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(AuthorizedUsersUiState.Error)
            }

    /** La lista depende de quién tenga la sesión: cambia si cambia la cuenta. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeOwned(): Flow<Owned?> =
        authRepository.currentUser
            .distinctUntilChanged()
            .flatMapLatest { owner ->
                if (owner == null) {
                    flowOf(null)
                } else {
                    authorizedUsersRepository.observeUsers(owner.uid)
                        .map { users -> Owned(owner = owner, users = users) }
                }
            }

    private suspend fun currentOwnerId(): String? =
        try {
            authRepository.currentUser.first()?.uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /**
     * Fila del dueño. Si la cuenta no tiene nombre se usa su correo, que es
     * también un dato real. Su último acceso al estuche no se registra.
     */
    private fun AuthUser.toItem(): AuthorizedUserItem {
        val name = displayName?.takeIf { it.isNotBlank() } ?: email.orEmpty()
        return AuthorizedUserItem(
            id = uid,
            name = name,
            initials = initialsOf(name),
            role = UserRole.PRINCIPAL,
            lastAccess = null,
            removable = false
        )
    }

    private fun AuthorizedUser.toItem(): AuthorizedUserItem = AuthorizedUserItem(
        id = id,
        name = name,
        initials = initialsOf(name),
        role = role,
        lastAccess = lastAccessAt?.let { at ->
            LastAccess(atMillis = at, day = lastAccessDayOf(accessAt = at, now = clock(), zone = zone()))
        },
        removable = true
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                AuthorizedUsersViewModel(
                    authRepository = application.container.authRepository,
                    authorizedUsersRepository = application.container.authorizedUsersRepository
                )
            }
        }
    }
}
