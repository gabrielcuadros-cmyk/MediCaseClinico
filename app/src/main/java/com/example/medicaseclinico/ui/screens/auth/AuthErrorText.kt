package com.example.medicaseclinico.ui.screens.auth

import androidx.annotation.StringRes
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.AuthError

/**
 * Mensaje en español de cada motivo de fallo de autenticación.
 *
 * Es un único lugar para que Iniciar sesión, Crear cuenta y Recuperar
 * contraseña expliquen el mismo fallo con las mismas palabras.
 */
@StringRes
fun AuthError.messageRes(): Int = when (this) {
    AuthError.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
    AuthError.INVALID_EMAIL -> R.string.auth_error_invalid_email
    AuthError.EMAIL_ALREADY_IN_USE -> R.string.auth_error_email_in_use
    AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthError.USER_DISABLED -> R.string.auth_error_user_disabled
    AuthError.TOO_MANY_REQUESTS -> R.string.auth_error_too_many_requests
    AuthError.NETWORK -> R.string.auth_error_network
    AuthError.NOT_CONFIGURED -> R.string.auth_error_not_configured
    AuthError.UNKNOWN -> R.string.auth_error_unknown
}
