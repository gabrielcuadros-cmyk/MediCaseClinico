package com.example.medicaseclinico.ui.screens.security

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.UserRole
import com.example.medicaseclinico.ui.components.MediCaseLoadingState
import com.example.medicaseclinico.ui.components.MediCaseMessageState
import com.example.medicaseclinico.ui.components.labelRes
import com.example.medicaseclinico.ui.screens.security.components.ChangeCasePinDialog
import com.example.medicaseclinico.ui.screens.security.components.ConfirmCasePinDialog
import com.example.medicaseclinico.ui.theme.MediCaseTheme
import kotlinx.coroutines.launch

private val ScreenPadding = 16.dp
private val CardShape = RoundedCornerShape(14.dp)
private val ButtonShape = RoundedCornerShape(12.dp)

/**
 * Entrada de la pestaña Seguridad: conecta el ViewModel con la pantalla sin
 * estado y muestra el resultado de cada acción.
 *
 * @param onSignOut cierra la sesión, o null si esta instalación no usa cuentas.
 */
@Composable
fun SecurityRoute(
    onOpenUsers: () -> Unit,
    onAddUser: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSignOut: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: SecurityViewModel = viewModel(factory = SecurityViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val message = (uiState as? SecurityUiState.Success)?.message
    val messageText = message?.let { stringResource(it.textRes()) }
    LaunchedEffect(message) {
        if (messageText == null) return@LaunchedEffect
        // Se da por mostrado antes de enseñarlo: si no, al girar el teléfono
        // mientras está en pantalla volvería a salir.
        viewModel.onMessageShown()
        scope.launch { snackbarHostState.showSnackbar(messageText) }
    }

    SecurityScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onSetPinRequired = viewModel::setPinRequired,
        onChangePin = viewModel::changePin,
        onRemoveUser = viewModel::removeUser,
        onOpenUsers = onOpenUsers,
        onAddUser = onAddUser,
        onOpenNotifications = onOpenNotifications,
        onSignOut = onSignOut,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

/**
 * Pantalla "Seguridad del Estuche": seguro y contraseña del estuche, personas
 * con acceso y los ajustes de la cuenta.
 */
@Composable
fun SecurityScreen(
    uiState: SecurityUiState,
    snackbarHostState: SnackbarHostState,
    onSetPinRequired: (required: Boolean, currentPin: String) -> Unit,
    onChangePin: (currentPin: String, newPin: String) -> Unit,
    onRemoveUser: (userId: String) -> Unit,
    onOpenUsers: () -> Unit,
    onAddUser: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSignOut: (() -> Unit)?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Pestaña principal: sin flecha "Atrás", como en el diseño. Al no
            // haber barra superior, el título debe esquivar la barra de estado.
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = ScreenPadding, end = ScreenPadding, top = 20.dp)
            ) {
                Text(
                    text = stringResource(R.string.security_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.security_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (uiState) {
                SecurityUiState.Loading ->
                    MediCaseLoadingState(message = stringResource(R.string.security_loading))

                is SecurityUiState.Success ->
                    SecurityContent(
                        state = uiState,
                        onSetPinRequired = onSetPinRequired,
                        onChangePin = onChangePin,
                        onRemoveUser = onRemoveUser,
                        onOpenUsers = onOpenUsers,
                        onAddUser = onAddUser,
                        onOpenNotifications = onOpenNotifications,
                        onSignOut = onSignOut,
                        onRetry = onRetry
                    )

                SecurityUiState.Error ->
                    MediCaseMessageState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = stringResource(R.string.security_error_title),
                        message = stringResource(R.string.security_error_message),
                        accentColor = MaterialTheme.colorScheme.error,
                        accentContainerColor = MaterialTheme.colorScheme.errorContainer,
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = onRetry
                    )
            }
        }
    }
}

/** Qué diálogo está abierto. Estado solo visual; no lleva ninguna contraseña. */
private enum class PinDialog { ENABLE_LOCK, DISABLE_LOCK, CHANGE_PIN }

@Composable
private fun SecurityContent(
    state: SecurityUiState.Success,
    onSetPinRequired: (required: Boolean, currentPin: String) -> Unit,
    onChangePin: (currentPin: String, newPin: String) -> Unit,
    onRemoveUser: (userId: String) -> Unit,
    onOpenUsers: () -> Unit,
    onAddUser: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSignOut: (() -> Unit)?,
    onRetry: () -> Unit
) {
    var pinDialog by rememberSaveable { mutableStateOf<PinDialog?>(null) }
    var pendingRemovalId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingRemoval = state.users.firstOrNull { it.id == pendingRemovalId }

    when (pinDialog) {
        PinDialog.ENABLE_LOCK, PinDialog.DISABLE_LOCK -> {
            val enabling = pinDialog == PinDialog.ENABLE_LOCK
            ConfirmCasePinDialog(
                enabling = enabling,
                onConfirm = { currentPin ->
                    pinDialog = null
                    onSetPinRequired(enabling, currentPin)
                },
                onDismiss = { pinDialog = null }
            )
        }
        PinDialog.CHANGE_PIN ->
            ChangeCasePinDialog(
                onConfirm = { currentPin, newPin ->
                    pinDialog = null
                    onChangePin(currentPin, newPin)
                },
                onDismiss = { pinDialog = null }
            )
        null -> Unit
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PinLockCard(
            pin = state.pin,
            enabled = !state.isBusy,
            onToggle = { enable ->
                pinDialog = if (enable) PinDialog.ENABLE_LOCK else PinDialog.DISABLE_LOCK
            }
        )

        SectionHeader(text = stringResource(R.string.security_pin_section))
        CasePinRow(
            pin = state.pin,
            enabled = !state.isBusy,
            onChangeClick = { pinDialog = PinDialog.CHANGE_PIN }
        )

        // "Ver todos" no está en el diseño: es la entrada a Usuarios Autorizados.
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(
                text = stringResource(R.string.security_users_section),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenUsers) {
                Text(
                    text = stringResource(R.string.security_users_see_all),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        when {
            state.usersFailed -> UsersFailed(onRetry = onRetry)
            state.users.isEmpty() ->
                Text(
                    text = stringResource(R.string.security_users_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            else -> state.users.forEach { user ->
                AccessUserCard(
                    user = user,
                    enabled = !state.isBusy,
                    onRemoveClick = { pendingRemovalId = user.id }
                )
            }
        }
        AddUserButton(onClick = onAddUser)

        SectionHeader(text = stringResource(R.string.security_account_section))
        SettingsRow(
            icon = Icons.Outlined.NotificationsNone,
            text = stringResource(R.string.notif_settings_title),
            onClick = onOpenNotifications
        )
        if (onSignOut != null) {
            TextButton(
                onClick = onSignOut,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(text = stringResource(R.string.action_sign_out), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SecurityCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        content = content
    )
}

/**
 * Seguro del estuche. Toda la fila es el interruptor. Sin estuche que lo
 * confirme, el interruptor queda desactivado y se explica por qué: no se
 * enseña una posición que nadie ha informado.
 */
@Composable
private fun PinLockCard(
    pin: CasePinState,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val checked = pin.pinRequired == true
    val canToggle = enabled && pin.canChange && pin.pinRequired != null

    SecurityCard {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        enabled = canToggle,
                        role = Role.Switch,
                        onValueChange = onToggle
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.security_lock_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.security_lock_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                // onCheckedChange = null: el toque lo gestiona la fila entera.
                Switch(checked = checked, onCheckedChange = null, enabled = canToggle)
            }
            pin.availability.unavailableRes()?.let { reason ->
                Text(
                    text = stringResource(reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                )
            }
        }
    }
}

/**
 * La contraseña nunca se muestra porque la app no la conoce: con estuche
 * compatible se dibujan los cuatro puntos del diseño, y sin él "--".
 */
@Composable
private fun CasePinRow(
    pin: CasePinState,
    enabled: Boolean,
    onChangeClick: () -> Unit
) {
    val hiddenDescription = stringResource(R.string.security_pin_hidden_description)

    SecurityCard {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Keyboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            if (pin.canChange) {
                Text(
                    text = stringResource(R.string.security_pin_mask),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = hiddenDescription }
                )
            } else {
                Text(
                    text = stringResource(R.string.value_unknown),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            IconButton(onClick = onChangeClick, enabled = enabled && pin.canChange) {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = stringResource(R.string.security_change_pin_action)
                )
            }
        }
    }
}

@Composable
private fun AccessUserCard(
    user: AccessUser,
    enabled: Boolean,
    onRemoveClick: () -> Unit
) {
    SecurityCard {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Iniciales en lugar de la foto del diseño: no hay fotos como dato.
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
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
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(user.role.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Rojo, como en Usuarios Autorizados: es una acción destructiva.
            IconButton(
                onClick = onRemoveClick,
                enabled = enabled,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.security_remove_user, user.name)
                )
            }
        }
    }
}

@Composable
private fun UsersFailed(onRetry: () -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.security_users_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.action_retry), fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Botón de borde punteado del diseño. */
@Composable
private fun AddUserButton(onClick: () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    )
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.security_add_user),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button },
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
private fun CasePinAvailability.unavailableRes(): Int? = when (this) {
    CasePinAvailability.READY -> null
    CasePinAvailability.DISCONNECTED -> R.string.security_case_disconnected
    CasePinAvailability.NOT_SUPPORTED -> R.string.security_case_not_supported
}

@StringRes
private fun SecurityMessage.textRes(): Int = when (this) {
    SecurityMessage.PIN_CHANGED -> R.string.security_msg_pin_changed
    SecurityMessage.LOCK_ENABLED -> R.string.security_msg_lock_enabled
    SecurityMessage.LOCK_DISABLED -> R.string.security_msg_lock_disabled
    SecurityMessage.USER_REMOVED -> R.string.security_msg_user_removed
    SecurityMessage.USER_REMOVE_FAILED -> R.string.security_msg_user_remove_failed
    SecurityMessage.CASE_NOT_CONNECTED -> R.string.security_case_disconnected
    SecurityMessage.CASE_NOT_SUPPORTED -> R.string.security_case_not_supported
    SecurityMessage.PAIRING_REQUIRED -> R.string.security_msg_pairing_required
    SecurityMessage.WRONG_PIN -> R.string.security_msg_wrong_pin
    SecurityMessage.CASE_LOCKED_OUT -> R.string.security_msg_locked_out
    SecurityMessage.INVALID_PIN -> R.string.security_msg_invalid_pin
    SecurityMessage.CASE_FAILED -> R.string.security_msg_case_failed
}

// ---------------------------------------------------------------------
//  Previews (los valores de ejemplo solo existen en el IDE)
// ---------------------------------------------------------------------
@Composable
private fun SecurityScreenPreview(uiState: SecurityUiState) {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SecurityScreen(
                uiState = uiState,
                snackbarHostState = remember { SnackbarHostState() },
                onSetPinRequired = { _, _ -> },
                onChangePin = { _, _ -> },
                onRemoveUser = {},
                onOpenUsers = {},
                onAddUser = {},
                onOpenNotifications = {},
                onSignOut = {},
                onRetry = {}
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 900, name = "Seguridad · estuche compatible")
@Composable
private fun SecurityReadyPreview() {
    SecurityScreenPreview(
        SecurityUiState.Success(
            pin = CasePinState(availability = CasePinAvailability.READY, pinRequired = true),
            users = listOf(
                AccessUser(id = "1", name = "Sofía Martínez", initials = "SM", role = UserRole.CAREGIVER),
                AccessUser(id = "2", name = "Dr. Luis Ramos", initials = "DL", role = UserRole.SECONDARY_ADMIN)
            ),
            usersFailed = false,
            isBusy = false,
            message = null
        )
    )
}

@Preview(showBackground = true, heightDp = 900, name = "Seguridad · estuche no compatible")
@Composable
private fun SecurityNotSupportedPreview() {
    SecurityScreenPreview(
        SecurityUiState.Success(
            pin = CasePinState(availability = CasePinAvailability.NOT_SUPPORTED, pinRequired = null),
            users = emptyList(),
            usersFailed = false,
            isBusy = false,
            message = null
        )
    )
}

@Preview(showBackground = true, name = "Seguridad · error")
@Composable
private fun SecurityErrorPreview() {
    SecurityScreenPreview(SecurityUiState.Error)
}
