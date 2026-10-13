package com.example.medicaseclinico.ui.screens.users

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.UserRole
import com.example.medicaseclinico.domain.time.DateTimeLabels
import com.example.medicaseclinico.domain.user.LastAccessDay
import com.example.medicaseclinico.ui.components.MediCaseBackButton
import com.example.medicaseclinico.ui.components.MediCaseLoadingState
import com.example.medicaseclinico.ui.components.MediCaseMessageState
import com.example.medicaseclinico.ui.screens.users.components.RoleChip
import com.example.medicaseclinico.ui.theme.MediCaseTheme
import kotlinx.coroutines.launch

private val ScreenPadding = 16.dp
private val CardSpacing = 12.dp
private val CardShape = RoundedCornerShape(14.dp)
private val ButtonShape = RoundedCornerShape(12.dp)

/**
 * Entrada de "Usuarios Autorizados": conecta el ViewModel con la pantalla sin
 * estado y muestra el resultado de cada acción.
 */
@Composable
fun AuthorizedUsersRoute(
    onBack: () -> Unit,
    onAddUser: () -> Unit,
    onOpenPermissions: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthorizedUsersViewModel = viewModel(factory = AuthorizedUsersViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val message = (uiState as? AuthorizedUsersUiState.Success)?.message
    val messageText = message?.let { stringResource(it.textRes()) }
    LaunchedEffect(message) {
        if (messageText == null) return@LaunchedEffect
        // Se da por mostrado antes de enseñarlo: si no, al girar el teléfono
        // mientras está en pantalla volvería a salir.
        viewModel.onMessageShown()
        scope.launch { snackbarHostState.showSnackbar(messageText) }
    }

    AuthorizedUsersScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAddUser = onAddUser,
        onOpenPermissions = onOpenPermissions,
        onRemoveUser = viewModel::removeUser,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

/**
 * Pantalla "Usuarios Autorizados": quién puede ver o administrar el estuche.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorizedUsersScreen(
    uiState: AuthorizedUsersUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAddUser: () -> Unit,
    onOpenPermissions: (userId: String) -> Unit,
    onRemoveUser: (userId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.users_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                text = stringResource(R.string.users_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenPadding)
            )

            when (uiState) {
                AuthorizedUsersUiState.Loading ->
                    MediCaseLoadingState(message = stringResource(R.string.users_loading))

                AuthorizedUsersUiState.Empty ->
                    MediCaseMessageState(
                        icon = Icons.Outlined.PeopleOutline,
                        title = stringResource(R.string.users_empty_title),
                        message = stringResource(R.string.users_empty_message),
                        accentColor = MaterialTheme.colorScheme.primary,
                        accentContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )

                is AuthorizedUsersUiState.Success ->
                    UsersContent(
                        state = uiState,
                        onAddUser = onAddUser,
                        onOpenPermissions = onOpenPermissions,
                        onRemoveUser = onRemoveUser
                    )

                AuthorizedUsersUiState.Error ->
                    MediCaseMessageState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = stringResource(R.string.users_error_title),
                        message = stringResource(R.string.users_error_message),
                        accentColor = MaterialTheme.colorScheme.error,
                        accentContainerColor = MaterialTheme.colorScheme.errorContainer,
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = onRetry
                    )
            }
        }
    }
}

@Composable
private fun UsersContent(
    state: AuthorizedUsersUiState.Success,
    onAddUser: () -> Unit,
    onOpenPermissions: (userId: String) -> Unit,
    onRemoveUser: (userId: String) -> Unit
) {
    var pendingRemovalId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingRemoval = state.users.firstOrNull { it.id == pendingRemovalId && it.removable }

    if (pendingRemoval != null) {
        RemoveUserDialog(
            userName = pendingRemoval.name,
            onConfirm = {
                pendingRemovalId = null
                onRemoveUser(pendingRemoval.id)
            },
            onDismiss = { pendingRemovalId = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(CardSpacing)
        ) {
            items(items = state.users, key = { it.id }) { user ->
                UserCard(
                    user = user,
                    enabled = !state.isBusy,
                    onClick = { onOpenPermissions(user.id) },
                    onRemoveClick = { pendingRemovalId = user.id }
                )
            }
        }
        Button(
            onClick = onAddUser,
            shape = ButtonShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenPadding, end = ScreenPadding, bottom = ScreenPadding)
                .heightIn(min = 52.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.PersonAddAlt1,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.security_add_user),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun UserCard(
    user: AuthorizedUserItem,
    enabled: Boolean,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val displayName = user.name.ifEmpty { stringResource(R.string.value_unknown) }
    val openPermissionsLabel = stringResource(R.string.users_open_permissions, displayName)

    Surface(
        // Solo los invitados tienen permisos que ajustar: el dueño los tiene todos.
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .then(
                if (user.removable) {
                    Modifier.clickable(
                        onClickLabel = openPermissionsLabel,
                        role = Role.Button,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 68.dp)
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Iniciales en lugar de la foto del diseño: no hay fotos como dato.
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = user.initials,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    RoleChip(role = user.role)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (user.invitationPending) {
                        stringResource(R.string.users_invitation_pending)
                    } else {
                        stringResource(R.string.users_last_access, lastAccessText(user.lastAccess))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (user.removable) {
                IconButton(
                    onClick = onRemoveClick,
                    enabled = enabled,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.security_remove_user, displayName)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

/** "Hoy, 08:15 a. m.", "Ayer, 06:40 p. m.", "12 oct, 11:20 a. m." o "--". */
@Composable
private fun lastAccessText(lastAccess: LastAccess?): String {
    if (lastAccess == null) return stringResource(R.string.value_unknown)

    val dateTimeLabels = remember { DateTimeLabels.system() }
    val formatted = remember(dateTimeLabels, lastAccess) {
        if (lastAccess.day == LastAccessDay.EARLIER) {
            dateTimeLabels.accessDateTime(lastAccess.atMillis)
        } else {
            dateTimeLabels.accessTime(lastAccess.atMillis)
        }
    }
    return when (lastAccess.day) {
        LastAccessDay.TODAY -> stringResource(R.string.users_last_access_today, formatted)
        LastAccessDay.YESTERDAY -> stringResource(R.string.users_last_access_yesterday, formatted)
        LastAccessDay.EARLIER -> formatted
    }
}

/**
 * Quitar el acceso a una persona no se puede deshacer desde la app: se
 * pregunta antes, diciendo de quién se trata.
 */
@Composable
private fun RemoveUserDialog(
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
private fun AuthorizedUsersMessage.textRes(): Int = when (this) {
    AuthorizedUsersMessage.USER_REMOVED -> R.string.security_msg_user_removed
    AuthorizedUsersMessage.USER_REMOVE_FAILED -> R.string.security_msg_user_remove_failed
}

// ---------------------------------------------------------------------
//  Previews (los valores de ejemplo solo existen en el IDE)
// ---------------------------------------------------------------------
@Composable
private fun AuthorizedUsersScreenPreview(uiState: AuthorizedUsersUiState) {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AuthorizedUsersScreen(
                uiState = uiState,
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onAddUser = {},
                onOpenPermissions = {},
                onRemoveUser = {},
                onRetry = {}
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 700, name = "Usuarios · lista")
@Composable
private fun AuthorizedUsersListPreview() {
    AuthorizedUsersScreenPreview(
        AuthorizedUsersUiState.Success(
            users = listOf(
                AuthorizedUserItem(
                    id = "0",
                    name = "Dr. Alejandro Díaz",
                    initials = "DA",
                    role = UserRole.PRINCIPAL,
                    lastAccess = null,
                    invitationPending = false,
                    removable = false
                ),
                AuthorizedUserItem(
                    id = "1",
                    name = "Sra. Laura Gómez",
                    initials = "SL",
                    role = UserRole.CAREGIVER,
                    lastAccess = LastAccess(atMillis = 0L, day = LastAccessDay.YESTERDAY),
                    invitationPending = false,
                    removable = true
                )
            ),
            isBusy = false,
            message = null
        )
    )
}

@Preview(showBackground = true, name = "Usuarios · sin sesión")
@Composable
private fun AuthorizedUsersEmptyPreview() {
    AuthorizedUsersScreenPreview(AuthorizedUsersUiState.Empty)
}

@Preview(showBackground = true, name = "Usuarios · error")
@Composable
private fun AuthorizedUsersErrorPreview() {
    AuthorizedUsersScreenPreview(AuthorizedUsersUiState.Error)
}
