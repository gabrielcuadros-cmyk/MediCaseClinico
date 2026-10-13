package com.example.medicaseclinico.ui.screens.users.add

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.UserPermission
import com.example.medicaseclinico.domain.model.UserRole
import com.example.medicaseclinico.domain.model.isGranted
import com.example.medicaseclinico.ui.components.MediCaseBackButton
import com.example.medicaseclinico.ui.components.labelRes
import com.example.medicaseclinico.ui.screens.auth.components.AuthErrorMessage
import com.example.medicaseclinico.ui.screens.auth.components.AuthPrimaryButton
import com.example.medicaseclinico.ui.screens.auth.components.AuthTextField
import com.example.medicaseclinico.ui.theme.MediCaseTheme

private val ScreenPadding = 16.dp
private val SegmentShape = RoundedCornerShape(9.dp)
private const val TEXT_MIME_TYPE = "text/plain"

/** Roles que se pueden asignar a un invitado. El principal es siempre el dueño. */
private val AssignableRoles = listOf(UserRole.CAREGIVER, UserRole.SECONDARY_ADMIN)

/** Los cuatro "Permisos iniciales" del diseño, en su orden. */
private val InitialPermissions = listOf(
    UserPermission.MONITOR_TEMPERATURE,
    UserPermission.VIEW_LOCATION,
    UserPermission.VIEW_ACCESS_LOGS,
    UserPermission.RECEIVE_CRITICAL_ALERTS
)

/**
 * Entrada de "Nuevo Usuario": conecta el ViewModel con la pantalla sin estado
 * y, cuando la persona queda guardada, abre el menú de compartir con la
 * invitación ya escrita.
 *
 * @param onDone la persona quedó registrada: la pantalla debe salir.
 */
@Composable
fun AddUserRoute(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddUserViewModel = viewModel(factory = AddUserViewModel.Factory)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val invitation = uiState.invitation
    val invitationText = invitation?.let {
        stringResource(R.string.add_user_invitation_text, it.name, stringResource(it.role.labelRes()))
    }
    val invitationSubject = stringResource(R.string.add_user_invitation_subject)
    val chooserTitle = stringResource(R.string.add_user_share_title)
    val shareUnavailable = stringResource(R.string.add_user_share_unavailable)

    LaunchedEffect(invitation) {
        if (invitationText == null) return@LaunchedEffect
        // Se da por atendida antes de abrir el menú: si no, al volver a la
        // pantalla se abriría otra vez.
        viewModel.onInvitationHandled()
        val shared = context.shareInvitation(
            text = invitationText,
            subject = invitationSubject,
            chooserTitle = chooserTitle
        )
        if (!shared) {
            // La persona ya quedó guardada; solo falta avisarle por otro medio.
            Toast.makeText(context, shareUnavailable, Toast.LENGTH_LONG).show()
        }
        onDone()
    }

    AddUserScreen(
        uiState = uiState,
        name = viewModel.name,
        email = viewModel.email,
        phone = viewModel.phone,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPhoneChange = viewModel::onPhoneChange,
        onRoleSelected = viewModel::onRoleSelected,
        onPermissionToggled = viewModel::onPermissionToggled,
        onSendInvitation = viewModel::sendInvitation,
        onBack = onBack,
        modifier = modifier
    )
}

/**
 * Abre el menú de compartir de Android con el texto de la invitación.
 * Protegido: si ninguna app puede recibirlo, devuelve false en lugar de
 * cerrar la app.
 */
private fun Context.shareInvitation(text: String, subject: String, chooserTitle: String): Boolean {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = TEXT_MIME_TYPE
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    return try {
        startActivity(Intent.createChooser(sendIntent, chooserTitle))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

/**
 * Pantalla "Nuevo Usuario": datos de la persona, su rol y los permisos con los
 * que empieza.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUserScreen(
    uiState: AddUserUiState,
    name: String,
    email: String,
    phone: String,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onRoleSelected: (UserRole) -> Unit,
    onPermissionToggled: (UserPermission, Boolean) -> Unit,
    onSendInvitation: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = !uiState.isSubmitting

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.add_user_title),
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
            // Fuera del scroll para que invitar esté siempre a mano.
            AuthPrimaryButton(
                text = stringResource(R.string.add_user_submit),
                onClick = onSendInvitation,
                isLoading = uiState.isSubmitting,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, end = ScreenPadding, bottom = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.add_user_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AuthTextField(
                label = stringResource(R.string.register_name_label),
                value = name,
                onValueChange = onNameChange,
                placeholder = stringResource(R.string.add_user_name_placeholder),
                errorText = uiState.nameError?.let { stringResource(it.messageRes()) },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )
            AuthTextField(
                label = stringResource(R.string.auth_email_label),
                value = email,
                onValueChange = onEmailChange,
                placeholder = stringResource(R.string.auth_email_placeholder),
                errorText = uiState.emailError?.let { stringResource(it.messageRes()) },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )
            AuthTextField(
                label = stringResource(R.string.register_phone_label),
                value = phone,
                onValueChange = onPhoneChange,
                placeholder = stringResource(R.string.register_phone_placeholder),
                errorText = uiState.phoneError?.let { stringResource(it.messageRes()) },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                )
            )

            FieldLabel(text = stringResource(R.string.add_user_role_label))
            RoleSelector(
                selectedRole = uiState.role,
                enabled = enabled,
                onRoleSelected = onRoleSelected
            )

            FieldLabel(text = stringResource(R.string.add_user_permissions_label))
            Column {
                InitialPermissions.forEach { permission ->
                    PermissionCheckRow(
                        text = stringResource(permission.initialLabelRes()),
                        checked = uiState.permissions.isGranted(permission),
                        enabled = enabled,
                        onCheckedChange = { granted -> onPermissionToggled(permission, granted) }
                    )
                }
            }

            uiState.error?.let { error ->
                AuthErrorMessage(text = stringResource(error.messageRes()))
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Selector segmentado del rol, con el mismo aspecto que el de rango del
 * Historial Térmico: opción activa blanca sobre fondo gris. Cada segmento se
 * anuncia como seleccionado o no, para que no dependa solo del color.
 */
@Composable
private fun RoleSelector(
    selectedRole: UserRole,
    enabled: Boolean,
    onRoleSelected: (UserRole) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(4.dp)
        ) {
            for (role in AssignableRoles) {
                val isSelected = role == selectedRole
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(SegmentShape)
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onRoleSelected(role) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.matchParentSize(),
                        shape = SegmentShape,
                        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                    ) {}
                    Text(
                        text = stringResource(role.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}

/** Toda la fila es la casilla, para que sea fácil de tocar y se lea como una sola cosa. */
@Composable
private fun PermissionCheckRow(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // onCheckedChange = null: el toque lo gestiona la fila entera.
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@StringRes
private fun UserPermission.initialLabelRes(): Int = when (this) {
    UserPermission.MONITOR_TEMPERATURE -> R.string.add_user_permission_temperature
    UserPermission.VIEW_LOCATION -> R.string.add_user_permission_location
    UserPermission.VIEW_ACCESS_LOGS -> R.string.add_user_permission_access_logs
    UserPermission.RECEIVE_CRITICAL_ALERTS -> R.string.add_user_permission_alerts
    UserPermission.EXPORT_REPORTS -> R.string.add_user_permission_reports
    UserPermission.MANAGE_USERS -> R.string.add_user_permission_manage_users
}

@StringRes
private fun AddUserFieldError.messageRes(): Int = when (this) {
    AddUserFieldError.NAME_REQUIRED -> R.string.add_user_field_name_required
    AddUserFieldError.EMAIL_REQUIRED -> R.string.add_user_field_email_required
    AddUserFieldError.EMAIL_INVALID -> R.string.auth_error_invalid_email
    AddUserFieldError.EMAIL_ALREADY_INVITED -> R.string.add_user_field_email_already_invited
    AddUserFieldError.PHONE_REQUIRED -> R.string.add_user_field_phone_required
    AddUserFieldError.PHONE_INVALID -> R.string.auth_field_phone_invalid
}

@StringRes
private fun AddUserError.messageRes(): Int = when (this) {
    AddUserError.NO_SESSION -> R.string.add_user_error_no_session
    AddUserError.SAVE_FAILED -> R.string.add_user_error_save_failed
}

// ---------------------------------------------------------------------
//  Previews (los valores de ejemplo solo existen en el IDE)
// ---------------------------------------------------------------------
@Composable
private fun AddUserScreenPreview(uiState: AddUserUiState, name: String = "") {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AddUserScreen(
                uiState = uiState,
                name = name,
                email = "",
                phone = "",
                onNameChange = {},
                onEmailChange = {},
                onPhoneChange = {},
                onRoleSelected = {},
                onPermissionToggled = { _, _ -> },
                onSendInvitation = {},
                onBack = {}
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 800, name = "Nuevo Usuario · vacío")
@Composable
private fun AddUserEmptyPreview() {
    AddUserScreenPreview(AddUserUiState())
}

@Preview(showBackground = true, heightDp = 900, name = "Nuevo Usuario · errores")
@Composable
private fun AddUserErrorsPreview() {
    AddUserScreenPreview(
        uiState = AddUserUiState(
            emailError = AddUserFieldError.EMAIL_REQUIRED,
            phoneError = AddUserFieldError.PHONE_INVALID,
            role = UserRole.SECONDARY_ADMIN,
            error = AddUserError.SAVE_FAILED
        ),
        name = "Laura Gómez"
    )
}
