package com.example.medicaseclinico.ui.screens.auth.register

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.auth.AuthValidation
import com.example.medicaseclinico.domain.model.AuthError
import com.example.medicaseclinico.ui.screens.auth.AuthFieldError
import com.example.medicaseclinico.ui.screens.auth.components.AuthErrorMessage
import com.example.medicaseclinico.ui.screens.auth.components.AuthPrimaryButton
import com.example.medicaseclinico.ui.screens.auth.components.AuthTextField
import com.example.medicaseclinico.ui.screens.auth.components.MediCaseLogo
import com.example.medicaseclinico.ui.screens.auth.messageRes
import com.example.medicaseclinico.ui.theme.MediCaseTheme

private val ScreenPadding = 24.dp
private val FieldSpacing = 14.dp

/**
 * Entrada de "Crear Cuenta": conecta el ViewModel con la pantalla sin estado y
 * avisa a la navegación cuando la cuenta queda creada.
 */
@Composable
fun RegisterRoute(
    onRegistered: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnRegistered by rememberUpdatedState(onRegistered)

    LaunchedEffect(uiState.isRegistered) {
        if (uiState.isRegistered) currentOnRegistered()
    }

    RegisterScreen(
        uiState = uiState,
        fields = RegisterFields(
            name = viewModel.name,
            email = viewModel.email,
            phone = viewModel.phone,
            password = viewModel.password,
            confirmPassword = viewModel.confirmPassword
        ),
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPhoneChange = viewModel::onPhoneChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onTermsAcceptedChange = viewModel::onTermsAcceptedChange,
        onRegister = viewModel::register,
        onSignIn = onSignIn,
        modifier = modifier
    )
}

/** Lo que hay escrito en cada campo, agrupado para no pasar cinco textos sueltos. */
data class RegisterFields(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmPassword: String = ""
)

/**
 * Pantalla "Crear Cuenta": nombre, correo, teléfono, contraseña dos veces y
 * la aceptación de los términos.
 */
@Composable
fun RegisterScreen(
    uiState: RegisterUiState,
    fields: RegisterFields,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTermsAcceptedChange: (Boolean) -> Unit,
    onRegister: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onRegister()
    }
    val enabled = !uiState.isSubmitting
    val next = KeyboardOptions(imeAction = ImeAction.Next)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Con scroll: son cinco campos y el teclado tapa media pantalla.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 24.dp)
        ) {
            MediCaseLogo(
                logoWidth = 120.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.register_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.register_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
                AuthTextField(
                    label = stringResource(R.string.register_name_label),
                    value = fields.name,
                    onValueChange = onNameChange,
                    placeholder = stringResource(R.string.register_name_placeholder),
                    errorText = uiState.nameError.text(),
                    enabled = enabled,
                    keyboardOptions = next.copy(capitalization = KeyboardCapitalization.Words)
                )
                AuthTextField(
                    label = stringResource(R.string.auth_email_label),
                    value = fields.email,
                    onValueChange = onEmailChange,
                    placeholder = stringResource(R.string.auth_email_placeholder),
                    errorText = uiState.emailError.text(),
                    enabled = enabled,
                    keyboardOptions = next.copy(keyboardType = KeyboardType.Email)
                )
                AuthTextField(
                    label = stringResource(R.string.register_phone_label),
                    value = fields.phone,
                    onValueChange = onPhoneChange,
                    placeholder = stringResource(R.string.register_phone_placeholder),
                    errorText = uiState.phoneError.text(),
                    enabled = enabled,
                    keyboardOptions = next.copy(keyboardType = KeyboardType.Phone)
                )
                AuthTextField(
                    label = stringResource(R.string.auth_password_label),
                    value = fields.password,
                    onValueChange = onPasswordChange,
                    placeholder = stringResource(
                        R.string.register_password_placeholder,
                        AuthValidation.MIN_PASSWORD_LENGTH
                    ),
                    errorText = uiState.passwordError?.let {
                        stringResource(it.messageRes, AuthValidation.MIN_PASSWORD_LENGTH)
                    },
                    enabled = enabled,
                    isPassword = true,
                    keyboardOptions = next
                )
                AuthTextField(
                    label = stringResource(R.string.register_confirm_password_label),
                    value = fields.confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    placeholder = stringResource(R.string.register_confirm_password_placeholder),
                    errorText = uiState.confirmPasswordError.text(),
                    enabled = enabled,
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TermsCheckbox(
                accepted = uiState.termsAccepted,
                onAcceptedChange = onTermsAcceptedChange,
                errorText = uiState.termsError.text(),
                enabled = enabled
            )

            if (uiState.error != null) {
                AuthErrorMessage(
                    text = stringResource(uiState.error.messageRes()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            AuthPrimaryButton(
                text = stringResource(R.string.register_submit),
                onClick = submit,
                isLoading = uiState.isSubmitting
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.register_have_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.register_sign_in),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onSignIn)
                        .padding(horizontal = 6.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun AuthFieldError?.text(): String? = this?.let { stringResource(it.messageRes) }

/**
 * Toda la fila es la casilla: se puede tocar el texto, y los lectores de
 * pantalla la leen como un único control con su estado.
 */
@Composable
private fun TermsCheckbox(
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    errorText: String?,
    enabled: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = accepted,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onAcceptedChange
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // onCheckedChange = null: el toque lo gestiona la fila entera.
            Checkbox(checked = accepted, onCheckedChange = null, enabled = enabled)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.register_terms),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// ---------------------------------------------------------------------
//  Previews (los valores de ejemplo solo existen en el IDE)
// ---------------------------------------------------------------------
@Composable
private fun RegisterScreenPreview(
    uiState: RegisterUiState,
    fields: RegisterFields = RegisterFields()
) {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RegisterScreen(
                uiState = uiState,
                fields = fields,
                onNameChange = {},
                onEmailChange = {},
                onPhoneChange = {},
                onPasswordChange = {},
                onConfirmPasswordChange = {},
                onTermsAcceptedChange = {},
                onRegister = {},
                onSignIn = {}
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1000, name = "Registro · vacío")
@Composable
private fun RegisterEmptyPreview() {
    RegisterScreenPreview(uiState = RegisterUiState(termsAccepted = true))
}

@Preview(showBackground = true, heightDp = 1200, name = "Registro · errores")
@Composable
private fun RegisterErrorsPreview() {
    RegisterScreenPreview(
        uiState = RegisterUiState(
            nameError = AuthFieldError.NAME_REQUIRED,
            emailError = AuthFieldError.EMAIL_INVALID,
            phoneError = AuthFieldError.PHONE_INVALID,
            passwordError = AuthFieldError.PASSWORD_TOO_SHORT,
            confirmPasswordError = AuthFieldError.PASSWORDS_DO_NOT_MATCH,
            termsError = AuthFieldError.TERMS_REQUIRED,
            error = AuthError.EMAIL_ALREADY_IN_USE
        ),
        fields = RegisterFields(email = "doctor.diaz", phone = "123", password = "abc")
    )
}
