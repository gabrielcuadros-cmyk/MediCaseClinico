package com.example.medicaseclinico.ui.screens.users

import androidx.compose.runtime.Immutable
import com.example.medicaseclinico.domain.model.UserRole
import com.example.medicaseclinico.domain.user.LastAccessDay

/**
 * Estado único e inmutable de la pantalla "Usuarios Autorizados".
 */
@Immutable
sealed interface AuthorizedUsersUiState {

    /** Aún no hay respuesta de la nube. */
    data object Loading : AuthorizedUsersUiState

    /** No hay sesión: sin dueño no existe ninguna lista que mostrar. */
    data object Empty : AuthorizedUsersUiState

    /**
     * @param users el dueño de la cuenta primero y después las personas a las
     * que dio acceso, por orden alfabético.
     * @param isBusy hay un borrado en curso.
     * @param message aviso pendiente de mostrar tras una acción.
     */
    data class Success(
        val users: List<AuthorizedUserItem>,
        val isBusy: Boolean,
        val message: AuthorizedUsersMessage?
    ) : AuthorizedUsersUiState

    /** No se pudo leer la lista. Se ofrece reintentar. */
    data object Error : AuthorizedUsersUiState
}

/**
 * Persona de la lista, lista para dibujar.
 *
 * @param name nombre, o vacío si la cuenta no tiene ninguno: la pantalla
 * muestra entonces "--".
 * @param lastAccess último acceso al estuche, o null si no se conoce.
 * @param removable false para el dueño de la cuenta: no puede quitarse el
 * acceso a sí mismo.
 */
@Immutable
data class AuthorizedUserItem(
    val id: String,
    val name: String,
    val initials: String,
    val role: UserRole,
    val lastAccess: LastAccess?,
    val removable: Boolean
)

/** Momento del último acceso y si fue hoy, ayer o antes. */
@Immutable
data class LastAccess(
    val atMillis: Long,
    val day: LastAccessDay
)

/** Resultado de una acción, para avisar al usuario una sola vez. */
enum class AuthorizedUsersMessage {
    USER_REMOVED,
    USER_REMOVE_FAILED
}
