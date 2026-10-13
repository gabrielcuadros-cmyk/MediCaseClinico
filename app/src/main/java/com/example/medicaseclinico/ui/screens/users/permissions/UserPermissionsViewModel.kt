package com.example.medicaseclinico.ui.screens.users.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.UserPermission
import com.example.medicaseclinico.domain.model.isGranted
import com.example.medicaseclinico.domain.model.with
import com.example.medicaseclinico.domain.repository.AuthRepository
import com.example.medicaseclinico.domain.repository.AuthorizedUsersRepository
import com.example.medicaseclinico.domain.user.initialsOf
import com.example.medicaseclinico.ui.navigation.SecondaryRoutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Prepara la pantalla "Permisos de Usuario" de una persona y guarda cada
 * cambio de interruptor en cuanto se hace.
 *
 * Los permisos quedan guardados en la nube junto a la persona. Aplicarlos en
 * el teléfono del invitado es parte del acceso remoto, que es una mejora futura.
 *
 * @param userId registro de la persona dentro de la lista del dueño.
 */
class UserPermissionsViewModel(
    private val userId: String,
    private val authRepository: AuthRepository,
    private val authorizedUsersRepository: AuthorizedUsersRepository
) : ViewModel() {

    /** La persona tal como está guardada, junto a su dueño, para poder reescribirla. */
    private data class Owned(val ownerId: String, val user: AuthorizedUser)

    private var latest: Owned? = null
    private val saveFailed = MutableStateFlow(false)
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UserPermissionsUiState> =
        retryTrigger
            .flatMapLatest { observeScreen() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UserPermissionsUiState.Loading
            )

    /**
     * Concede o retira un permiso. Se parte de lo último leído de la nube, que
     * ya incluye los cambios anteriores aunque sigan de camino al servidor.
     */
    fun setPermission(permission: UserPermission, granted: Boolean) {
        val current = latest ?: return
        if (current.user.permissions.isGranted(permission) == granted) return

        val updated = current.user.copy(
            permissions = current.user.permissions.with(permission, granted)
        )
        // Sin esperar al servidor: dos toques seguidos no deben pisarse.
        latest = current.copy(user = updated)
        viewModelScope.launch {
            val result = authorizedUsersRepository.saveUser(ownerId = current.ownerId, user = updated)
            if (result.isFailure) saveFailed.value = true
        }
    }

    /** La pantalla ya mostró el aviso: no debe repetirse al girar el teléfono. */
    fun onSaveErrorShown() {
        saveFailed.value = false
    }

    /** Vuelve a leer a la persona tras un error. */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

    private fun observeScreen(): Flow<UserPermissionsUiState> =
        combine(observeOwned(), saveFailed) { owned, saveFailed ->
            if (owned == null) {
                UserPermissionsUiState.Empty
            } else {
                UserPermissionsUiState.Success(
                    name = owned.user.name,
                    initials = initialsOf(owned.user.name),
                    role = owned.user.role,
                    permissions = UserPermission.entries.map { permission ->
                        PermissionItem(
                            permission = permission,
                            granted = owned.user.permissions.isGranted(permission)
                        )
                    },
                    saveFailed = saveFailed
                )
            }
        }
            .onStart { emit(UserPermissionsUiState.Loading) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(UserPermissionsUiState.Error)
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeOwned(): Flow<Owned?> =
        authRepository.currentUser
            .map { it?.uid }
            .distinctUntilChanged()
            .flatMapLatest { ownerId ->
                if (ownerId == null) {
                    flowOf(null)
                } else {
                    authorizedUsersRepository.observeUsers(ownerId).map { users ->
                        users.firstOrNull { it.id == userId }?.let { Owned(ownerId = ownerId, user = it) }
                    }
                }
            }
            .onEach { latest = it }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                val userId: String = createSavedStateHandle()[SecondaryRoutes.USER_ID_ARG] ?: ""
                UserPermissionsViewModel(
                    userId = userId,
                    authRepository = application.container.authRepository,
                    authorizedUsersRepository = application.container.authorizedUsersRepository
                )
            }
        }
    }
}
