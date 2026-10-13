package com.example.medicaseclinico.ui.screens.users.permissions

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.UserPermission
import com.example.medicaseclinico.domain.model.UserRole
import com.example.medicaseclinico.ui.components.MediCaseBackButton
import com.example.medicaseclinico.ui.components.MediCaseLoadingState
import com.example.medicaseclinico.ui.components.MediCaseMessageState
import com.example.medicaseclinico.ui.components.labelRes
import com.example.medicaseclinico.ui.screens.users.components.RoleChip
import com.example.medicaseclinico.ui.theme.MediCaseTheme

private val ScreenPadding = 16.dp
private val CardShape = RoundedCornerShape(14.dp)
private val ButtonShape = RoundedCornerShape(12.dp)

/**
 * Entrada de "Permisos de Usuario": conecta el ViewModel con la pantalla sin
 * estado.
 */
@Composable
fun UserPermissionsRoute(
    onBack: () -> Unit,
    onRevoked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserPermissionsViewModel = viewModel(factory = UserPermissionsViewModel.Factory)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val revoked = uiState is UserPermissionsUiState.Revoked
    val revokedMessage = stringResource(R.string.security_msg_user_removed)
    LaunchedEffect(revoked) {
        if (revoked) {
            // La pantalla se cierra: el aviso tiene que sobrevivirla.
            Toast.makeText(context, revokedMessage, Toast.LENGTH_SHORT).show()
            onRevoked()
        }
    }

    UserPermissionsScreen(
        uiState = uiState,
        onBack = onBack,
        onPermissionToggled = viewModel::setPermission,
        onRevokeAccess = viewModel::revokeAccess,
        onSaveErrorShown = viewModel::onSaveErrorShown,
        onRevokeErrorShown = viewModel::onRevokeErrorShown,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

/**
 * Pantalla "Permisos de Usuario": qué puede hacer una persona autorizada, con
 * un interruptor por permiso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPermissionsScreen(
    uiState: UserPermissionsUiState,
    onBack: () -> Unit,
    onPermissionToggled: (UserPermission, Boolean) -> Unit,
    onRevokeAccess: () -> Unit,
    onSaveErrorShown: () -> Unit,
    onRevokeErrorShown: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.settings_save_error)
    val revokeErrorMessage = stringResource(R.string.security_msg_user_remove_failed)
    val success = uiState as? UserPermissionsUiState.Success
    val saveFailed = success?.saveFailed == true
    val revokeFailed = success?.revokeFailed == true
    // Estado solo visual: si el diálogo de confirmación está abierto.
    var confirmingRevoke by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            snackbarHostState.showSnackbar(saveErrorMessage)
            onSaveErrorShown()
        }
    }
    LaunchedEffect(revokeFailed) {
        if (revokeFailed) {
            snackbarHostState.showSnackbar(revokeErrorMessage)
            onRevokeErrorShown()
        }
    }

    if (confirmingRevoke && success != null) {
        RevokeAccessDialog(
            userName = success.name,
            onConfirm = {
                confirmingRevoke = false
                onRevokeAccess()
            },
            onDismiss = { confirmingRevoke = false }
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.user_permissions_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    MediCaseBackButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Fuera del scroll, como en el diseño: siempre al pie de la pantalla.
            if (success != null) {
                RevokeAccessButton(
                    isRevoking = success.isRevoking,
                    onClick = { confirmingRevoke = true },
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp)
                )
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (uiState) {
            // Revocado: se sigue viendo la carga el instante que tarda en cerrarse.
            UserPermissionsUiState.Loading, UserPermissionsUiState.Revoked ->
                MediCaseLoadingState(
                    message = stringResource(R.string.user_permissions_loading),
                    modifier = contentModifier
                )

            UserPermissionsUiState.Empty ->
                MediCaseMessageState(
                    icon = Icons.Outlined.PersonOff,
                    title = stringResource(R.string.user_permissions_empty_title),
                    message = stringResource(R.string.user_permissions_empty_message),
                    accentColor = MaterialTheme.colorScheme.primary,
                    accentContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = contentModifier
                )

            is UserPermissionsUiState.Success ->
                PermissionsContent(
                    state = uiState,
                    onPermissionToggled = onPermissionToggled,
                    modifier = contentModifier
                )

            UserPermissionsUiState.Error ->
                MediCaseMessageState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.users_error_title),
                    message = stringResource(R.string.users_error_message),
                    accentColor = MaterialTheme.colorScheme.error,
                    accentContainerColor = MaterialTheme.colorScheme.errorContainer,
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRetry,
                    modifier = contentModifier
                )
        }
    }
}

@Composable
private fun PermissionsContent(
    state: UserPermissionsUiState.Success,
    onPermissionToggled: (UserPermission, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        UserHeader(
            name = state.name,
            initials = state.initials,
            role = state.role,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        )
        state.permissions.forEach { item ->
            PermissionSwitchCard(
                title = stringResource(item.permission.titleRes()),
                description = stringResource(item.permission.descriptionRes()),
                checked = item.granted,
                enabled = !state.isRevoking,
                onCheckedChange = { granted -> onPermissionToggled(item.permission, granted) }
            )
        }
    }
}

@Composable
private fun UserHeader(
    name: String,
    initials: String,
    role: UserRole,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Iniciales en lugar de la foto del diseño: no hay fotos como dato.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clearAndSetSemantics { }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        RoleChip(
            role = role,
            text = stringResource(R.string.user_permissions_role, stringResource(role.labelRes()))
        )
    }
}

/** Toda la tarjeta es el interruptor, para que se lea y se toque como una sola cosa. */
@Composable
private fun PermissionSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // onCheckedChange = null: el toque lo gestiona la fila entera.
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

/**
 * Acción destructiva del diseño: fondo rojo tenue, borde y texto en Rojo de
 * Alerta. Mientras se quita el acceso se desactiva, para que un doble toque
 * no lance dos borrados.
 */
@Composable
private fun RevokeAccessButton(
    isRevoking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isRevoking,
        shape = ButtonShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        if (isRevoking) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.PersonRemove,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.user_permissions_revoke),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Quitar el acceso no se puede deshacer desde la app: se pregunta antes,
 * diciendo de quién se trata.
 */
@Composable
private fun RevokeAccessDialog(
    userName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.security_remove_user_title)) },
        text = { Text(text = stringResource(R.string.security_remove_user_message, userName)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(
                    text = stringResource(R.string.security_remove_user_confirm),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        }
    )
}

@StringRes
private fun UserPermission.titleRes(): Int = when (this) {
    UserPermission.MONITOR_TEMPERATURE -> R.string.user_permission_temperature_title
    UserPermission.VIEW_LOCATION -> R.string.user_permission_location_title
    UserPermission.VIEW_ACCESS_LOGS -> R.string.user_permission_access_logs_title
    UserPermission.RECEIVE_CRITICAL_ALERTS -> R.string.user_permission_alerts_title
    UserPermission.EXPORT_REPORTS -> R.string.user_permission_reports_title
    UserPermission.MANAGE_USERS -> R.string.user_permission_manage_users_title
}

@StringRes
private fun UserPermission.descriptionRes(): Int = when (this) {
    UserPermission.MONITOR_TEMPERATURE -> R.string.user_permission_temperature_description
    UserPermission.VIEW_LOCATION -> R.string.user_permission_location_description
    UserPermission.VIEW_ACCESS_LOGS -> R.string.user_permission_access_logs_description
    UserPermission.RECEIVE_CRITICAL_ALERTS -> R.string.user_permission_alerts_description
    UserPermission.EXPORT_REPORTS -> R.string.user_permission_reports_description
    UserPermission.MANAGE_USERS -> R.string.user_permission_manage_users_description
}

// ---------------------------------------------------------------------
//  Previews (los valores de ejemplo solo existen en el IDE)
// ---------------------------------------------------------------------
@Composable
private fun UserPermissionsScreenPreview(uiState: UserPermissionsUiState) {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            UserPermissionsScreen(
                uiState = uiState,
                onBack = {},
                onPermissionToggled = { _, _ -> },
                onRevokeAccess = {},
                onSaveErrorShown = {},
                onRevokeErrorShown = {},
                onRetry = {}
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 800, name = "Permisos · cuidador")
@Composable
private fun UserPermissionsSuccessPreview() {
    UserPermissionsScreenPreview(
        UserPermissionsUiState.Success(
            name = "Sra. Laura Gómez",
            initials = "SL",
            role = UserRole.CAREGIVER,
            permissions = UserPermission.entries.mapIndexed { index, permission ->
                PermissionItem(permission = permission, granted = index % 2 == 0)
            },
            saveFailed = false,
            isRevoking = false,
            revokeFailed = false
        )
    )
}

@Preview(showBackground = true, name = "Permisos · persona no encontrada")
@Composable
private fun UserPermissionsEmptyPreview() {
    UserPermissionsScreenPreview(UserPermissionsUiState.Empty)
}
