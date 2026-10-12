package com.example.medicaseclinico.ui.screens.auth

import androidx.annotation.StringRes
import com.example.medicaseclinico.R

/**
 * Errores de un campo que se detectan en el teléfono, antes de llamar al
 * servidor. Los decide el ViewModel; aquí solo se les pone texto.
 */
enum class AuthFieldError(@get:StringRes val messageRes: Int) {
    EMAIL_REQUIRED(R.string.auth_field_email_required),
    EMAIL_INVALID(R.string.auth_error_invalid_email),
    PASSWORD_REQUIRED(R.string.auth_field_password_required)
}
