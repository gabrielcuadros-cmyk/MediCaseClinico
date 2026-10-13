package com.example.medicaseclinico.ui.screens.users.permissions

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * Entrada de "Permisos de Usuario": conecta el ViewModel con la pantalla sin
 * estado.
 */
@Composable
fun UserPermissionsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserPermissionsViewModel = viewModel(factory = UserPermissionsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    UserPermissionsScreen(
        uiState = uiState,
        onBack = onBack,
        onPermissionToggled = viewModel::setPermission,
        onSaveErrorShown = viewModel::onSaveErrorShown,
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
    onSaveErrorShown: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.settings_save_error)
    val saveFailed = (uiState as? UserPermissionsUiState.Success)?.saveFailed == true

    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            snackbarHostState.showSnackbar(saveErrorMessage)
            onSaveErrorShown()
        }
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
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (uiState) {
            UserPermissionsUiState.Loading ->
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
            Switch(checked = checked, onCheckedChange = null)
        }
    }
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
                onSaveErrorShown = {},
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
            saveFailed = false
        )
    )
}

@Preview(showBackground = true, name = "Permisos · persona no encontrada")
@Composable
private fun UserPermissionsEmptyPreview() {
    UserPermissionsScreenPreview(UserPermissionsUiState.Empty)
}
