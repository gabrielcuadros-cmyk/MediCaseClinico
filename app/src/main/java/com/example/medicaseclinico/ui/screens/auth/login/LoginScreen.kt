package com.example.medicaseclinico.ui.screens.auth.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.AuthError
import com.example.medicaseclinico.ui.screens.auth.AuthFieldError
import com.example.medicaseclinico.ui.screens.auth.components.AuthErrorMessage
import com.example.medicaseclinico.ui.screens.auth.components.AuthPrimaryButton
import com.example.medicaseclinico.ui.screens.auth.components.AuthTextField
import com.example.medicaseclinico.ui.screens.auth.components.MediCaseLogo
import com.example.medicaseclinico.ui.screens.auth.messageRes
import com.example.medicaseclinico.ui.theme.MediCaseTheme

private val ScreenPadding = 24.dp

/**
 * Entrada de "Iniciar sesión": conecta el ViewModel con la pantalla sin estado
 * y avisa a la navegación cuando la sesión queda iniciada.
 */
@Composable
fun LoginRoute(
    onSignedIn: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)

    LaunchedEffect(uiState.isSignedIn) {
        if (uiState.isSignedIn) currentOnSignedIn()
    }

    LoginScreen(
        uiState = uiState,
        email = viewModel.email,
        password = viewModel.password,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSignIn = viewModel::signIn,
        onForgotPassword = onForgotPassword,
        onCreateAccount = onCreateAccount,
        modifier = modifier
    )
}

/**
 * Pantalla "Iniciar sesión": logotipo, correo, contraseña y accesos a
 * recuperar la contraseña y a crear una cuenta.
 */
@Composable
fun LoginScreen(
    uiState: LoginUiState,
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSignIn()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Con scroll para que el teclado no tape el botón en pantallas pequeñas.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            MediCaseLogo()
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
                    imeAction = ImeAction.Next
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            AuthTextField(
                label = stringResource(R.string.auth_password_label),
                value = password,
                onValueChange = onPasswordChange,
                errorText = uiState.passwordError?.let { stringResource(it.messageRes) },
                enabled = !uiState.isSubmitting,
                isPassword = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )

            TextButton(
                onClick = onForgotPassword,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = stringResource(R.string.login_forgot_password),
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (uiState.error != null) {
                AuthErrorMessage(
                    text = stringResource(uiState.error.messageRes()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            AuthPrimaryButton(
                text = stringResource(R.string.login_submit),
                onClick = submit,
                isLoading = uiState.isSubmitting
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.login_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.login_create_account),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onCreateAccount)
                        .padding(horizontal = 6.dp, vertical = 12.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Previews (los valores de ejemplo solo existen en el IDE)
// ---------------------------------------------------------------------
@Composable
private fun LoginScreenPreview(
    uiState: LoginUiState,
    email: String = "",
    password: String = ""
) {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LoginScreen(
                uiState = uiState,
                email = email,
                password = password,
                onEmailChange = {},
                onPasswordChange = {},
                onSignIn = {},
                onForgotPassword = {},
                onCreateAccount = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Login · relleno")
@Composable
private fun LoginFilledPreview() {
    LoginScreenPreview(
        uiState = LoginUiState(),
        email = "doctor.diaz@gmail.com",
        password = "contraseña12"
    )
}

@Preview(showBackground = true, name = "Login · errores")
@Composable
private fun LoginErrorsPreview() {
    LoginScreenPreview(
        uiState = LoginUiState(
            emailError = AuthFieldError.EMAIL_INVALID,
            passwordError = AuthFieldError.PASSWORD_REQUIRED,
            error = AuthError.INVALID_CREDENTIALS
        ),
        email = "doctor.diaz"
    )
}

@Preview(showBackground = true, name = "Login · enviando")
@Composable
private fun LoginSubmittingPreview() {
    LoginScreenPreview(
        uiState = LoginUiState(isSubmitting = true),
        email = "doctor.diaz@gmail.com",
        password = "contraseña12"
    )
}
