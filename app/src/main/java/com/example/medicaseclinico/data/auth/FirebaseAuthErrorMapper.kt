package com.example.medicaseclinico.data.auth

import com.example.medicaseclinico.domain.model.AuthError
import com.example.medicaseclinico.domain.model.AuthException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException

/**
 * Traduce los códigos de error de Firebase Auth a los motivos del dominio.
 *
 * Trabaja con el código (un texto) y no con las clases de excepción para
 * poder probarse con tests JVM, sin Android ni Firebase.
 *
 * "Usuario no encontrado" y "contraseña incorrecta" se funden en un solo
 * motivo a propósito: distinguirlos le diría a cualquiera qué correos tienen
 * cuenta.
 */
internal fun authErrorFromCode(code: String?): AuthError = when (code) {
    "ERROR_INVALID_CREDENTIAL",
    "ERROR_WRONG_PASSWORD",
    "ERROR_USER_NOT_FOUND",
    "ERROR_INVALID_LOGIN_CREDENTIALS" -> AuthError.INVALID_CREDENTIALS

    "ERROR_INVALID_EMAIL",
    "ERROR_MISSING_EMAIL" -> AuthError.INVALID_EMAIL

    "ERROR_EMAIL_ALREADY_IN_USE",
    "ERROR_CREDENTIAL_ALREADY_IN_USE" -> AuthError.EMAIL_ALREADY_IN_USE

    "ERROR_WEAK_PASSWORD" -> AuthError.WEAK_PASSWORD
    "ERROR_USER_DISABLED" -> AuthError.USER_DISABLED
    "ERROR_TOO_MANY_REQUESTS" -> AuthError.TOO_MANY_REQUESTS
    "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.NETWORK
    else -> AuthError.UNKNOWN
}

/** Clasifica cualquier fallo de una llamada a Firebase Auth. */
internal fun Throwable.toAuthException(): AuthException {
    val error = when (this) {
        is AuthException -> return this
        is FirebaseNetworkException -> AuthError.NETWORK
        is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_REQUESTS
        is FirebaseAuthException -> authErrorFromCode(errorCode)
        else -> AuthError.UNKNOWN
    }
    return AuthException(error = error, cause = this)
}
