package com.example.medicaseclinico.ui.screens.auth.recovery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.AuthError
import com.example.medicaseclinico.ui.components.MediCaseBackButton
import com.example.medicaseclinico.ui.screens.auth.components.AuthErrorMessage
import com.example.medicaseclinico.ui.screens.auth.components.AuthPrimaryButton
import com.example.medicaseclinico.ui.screens.auth.components.AuthTextField
import com.example.medicaseclinico.ui.screens.auth.messageRes
import com.example.medicaseclinico.ui.theme.MediCaseTheme

private val ScreenPadding = 24.dp
private val BadgeSize = 88.dp

/**
 * Entrada de "Recuperar Contraseña": conecta el ViewModel con la pantalla sin estado.
 */
@Composable
fun PasswordRecoveryRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PasswordRecoveryViewModel = viewModel(factory = PasswordRecoveryViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PasswordRecoveryScreen(
        uiState = uiState,
        email = viewModel.email,
        onBack = onBack,
        onEmailChange = viewModel::onEmailChange,
        onSend = viewModel::sendResetLink,
        onUseAnotherEmail = viewModel::onUseAnotherEmail,
        modifier = modifier
    )
}

/**
 * Pantalla "Recuperar Contraseña": pide el correo y envía un enlace para
 * elegir una contraseña nueva. Tras enviarlo, el formulario se cambia por la
 * confirmación, para que quede claro que ya no hay nada más que hacer aquí.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordRecoveryScreen(
    uiState: PasswordRecoveryUiState,
    email: String,
    onBack: () -> Unit,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    onUseAnotherEmail: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.recovery_title),
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(bottom = ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.sentTo == null) {
                RecoveryForm(
                    uiState = uiState,
                    email = email,
                    onEmailChange = onEmailChange,
                    onSend = onSend,
                    onBack = onBack
                )
            } else {
                RecoverySent(
                    sentTo = uiState.sentTo,
                    onBack = onBack,
                    onUseAnotherEmail = onUseAnotherEmail
                )
            }
        }
    }
}

@Composable
private fun RecoveryForm(
    uiState: PasswordRecoveryUiState,
    email: String,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSend()
    }

    Text(
        text = stringResource(R.string.recovery_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(32.dp))
    RoundBadge(icon = Icons.Outlined.Lock)
    Spacer(modifier = Modifier.height(32.dp))

    AuthTextField(
        label = stringResource(R.string.auth_email_label),
        value = email,
        onValueChange = onEmailChange,
        placeholder = stringResource(R.string.auth_email_placeholder),
        errorText = uiState.emailError?.let { stringResource(it.messageRes) },
        enabled = !uiState.isSubmitting,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { submit() })
    )

    if (uiState.error != null) {
        AuthErrorMessage(
            text = stringResource(uiState.error.messageRes()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
    AuthPrimaryButton(
        text = stringResource(R.string.recovery_submit),
        onClick = submit,
        isLoading = uiState.isSubmitting
    )
    Spacer(modifier = Modifier.height(8.dp))
    BackToLoginButton(onClick = onBack)
}

/**
 * No afirma que el correo exista: se dice "si hay una cuenta…" para no
 * revelar a un extraño qué correos están registrados.
 */
@Composable
private fun RecoverySent(
    sentTo: String,
    onBack: () -> Unit,
    onUseAnotherEmail: () -> Unit
) {
    Spacer(modifier = Modifier.height(32.dp))
    RoundBadge(icon = Icons.Outlined.MarkEmailRead)
    Spacer(modifier = Modifier.height(24.dp))
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.recovery_sent_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recovery_sent_message, sentTo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
    Spacer(modifier = Modifier.height(32.dp))
    AuthPrimaryButton(
        text = stringResource(R.string.recovery_back_to_login),
        onClick = onBack
    )
    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onUseAnotherEmail) {
        Text(
            text = stringResource(R.string.recovery_use_another_email),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Ícono dentro del círculo azul tenue del diseño. Es decorativo: el texto ya lo explica. */
@Composable
private fun RoundBadge(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(BadgeSize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
private fun BackToLoginButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = stringResource(R.string.recovery_back_to_login),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------
//  Previews (los valores de ejemplo solo existen en el IDE)
// ---------------------------------------------------------------------
@Composable
private fun PasswordRecoveryPreview(uiState: PasswordRecoveryUiState, email: String = "") {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PasswordRecoveryScreen(
                uiState = uiState,
                email = email,
                onBack = {},
                onEmailChange = {},
                onSend = {},
                onUseAnotherEmail = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Recuperar · formulario")
@Composable
private fun PasswordRecoveryFormPreview() {
    PasswordRecoveryPreview(uiState = PasswordRecoveryUiState())
}

@Preview(showBackground = true, name = "Recuperar · error")
@Composable
private fun PasswordRecoveryErrorPreview() {
    PasswordRecoveryPreview(
        uiState = PasswordRecoveryUiState(error = AuthError.NETWORK),
        email = "doctor.diaz@gmail.com"
    )
}

@Preview(showBackground = true, name = "Recuperar · enviado")
@Composable
private fun PasswordRecoverySentPreview() {
    PasswordRecoveryPreview(
        uiState = PasswordRecoveryUiState(sentTo = "doctor.diaz@gmail.com")
    )
}
