package com.example.medicaseclinico.ui.screens.users.permissions

import androidx.compose.runtime.Immutable
import com.example.medicaseclinico.domain.model.UserPermission
import com.example.medicaseclinico.domain.model.UserRole

/**
 * Estado único e inmutable de la pantalla "Permisos de Usuario".
 */
@Immutable
sealed interface UserPermissionsUiState {

    /** Aún no hay respuesta de la nube. */
    data object Loading : UserPermissionsUiState

    /** La persona ya no está en la lista (se le quitó el acceso) o no hay sesión. */
    data object Empty : UserPermissionsUiState

    /**
     * @param permissions un interruptor por permiso, en el orden del diseño.
     * @param saveFailed el último cambio no se pudo guardar; aviso pendiente.
     * @param isRevoking se está quitando el acceso: no se admiten más cambios.
     * @param revokeFailed no se pudo quitar el acceso; aviso pendiente.
     */
    data class Success(
        val name: String,
        val initials: String,
        val role: UserRole,
        val permissions: List<PermissionItem>,
        val saveFailed: Boolean,
        val isRevoking: Boolean,
        val revokeFailed: Boolean
    ) : UserPermissionsUiState

    /** Se quitó el acceso a la persona: la pantalla debe salir. */
    data object Revoked : UserPermissionsUiState

    /** No se pudo leer a la persona. Se ofrece reintentar. */
    data object Error : UserPermissionsUiState
}

/** Un permiso y si la persona lo tiene. */
@Immutable
data class PermissionItem(
    val permission: UserPermission,
    val granted: Boolean
)
