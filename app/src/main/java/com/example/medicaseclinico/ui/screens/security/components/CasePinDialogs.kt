package com.example.medicaseclinico.ui.screens.security.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.CasePin

/*
 * Las contraseñas de estos diálogos viven solo en `remember` (nunca en
 * `rememberSaveable` ni en el ViewModel): desaparecen al cerrar el diálogo y
 * no se escriben en el estado guardado de la pantalla. Es parte de "la app
 * nunca guarda el PIN".
 */

/**
 * Pide la contraseña actual del estuche para activar o desactivar el seguro.
 *
 * @param enabling true si se va a activar el seguro; cambia la explicación.
 */
@Composable
fun ConfirmCasePinDialog(
    enabling: Boolean,
    onConfirm: (currentPin: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (enabling) R.string.security_enable_lock_title else R.string.security_disable_lock_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(
                        if (enabling) {
                            R.string.security_enable_lock_message
                        } else {
                            R.string.security_disable_lock_message
                        }
                    )
                )
                CasePinField(
                    label = stringResource(R.string.security_current_pin_label),
                    value = currentPin,
                    onValueChange = { currentPin = it },
                    imeAction = ImeAction.Done
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentPin) },
                enabled = CasePin.isValid(currentPin)
            ) {
                Text(text = stringResource(R.string.action_accept), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * Cambia la contraseña del estuche: la actual y la nueva dos veces, para que
 * un dedo mal puesto no deje el estuche con una contraseña que nadie conoce.
 */
@Composable
fun ChangeCasePinDialog(
    onConfirm: (currentPin: String, newPin: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var repeatedPin by remember { mutableStateOf("") }

    val mismatch = repeatedPin.length == CasePin.LENGTH && repeatedPin != newPin
    val canConfirm = CasePin.isValid(currentPin) && CasePin.isValid(newPin) && repeatedPin == newPin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.security_change_pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.security_change_pin_message))
                CasePinField(
                    label = stringResource(R.string.security_current_pin_label),
                    value = currentPin,
                    onValueChange = { currentPin = it }
                )
                CasePinField(
                    label = stringResource(R.string.security_new_pin_label),
                    value = newPin,
                    onValueChange = { newPin = it }
                )
                CasePinField(
                    label = stringResource(R.string.security_repeat_pin_label),
                    value = repeatedPin,
                    onValueChange = { repeatedPin = it },
                    errorText = if (mismatch) stringResource(R.string.security_pin_mismatch) else null,
                    imeAction = ImeAction.Done
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentPin, newPin) },
                enabled = canConfirm
            ) {
                Text(
                    text = stringResource(R.string.security_change_pin_confirm),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        }
    )
}

/** Campo de cuatro dígitos ocultos, con teclado numérico. Lo que no es un dígito no entra. */
@Composable
private fun CasePinField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> onValueChange(typed.filter { it in '0'..'9' }.take(CasePin.LENGTH)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label) },
        isError = errorText != null,
        supportingText = errorText?.let { { Text(text = it, color = MaterialTheme.colorScheme.error) } },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction
        )
    )
}
