package com.example.medicaseclinico.domain.model

/**
 * Usuario con sesión iniciada.
 *
 * @param displayName nombre que dio al registrarse, o null si la cuenta no lo
 * tiene: la UI muestra entonces un saludo sin nombre, nunca uno inventado.
 */
data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?
)

/**
 * Motivos por los que puede fallar una operación de autenticación.
 *
 * Es un tipo del dominio y no un texto: la UI lo traduce con sus recursos, y
 * el resto de la app no depende de los códigos de error de Firebase.
 */
enum class AuthError {
    /** Correo o contraseña incorrectos. No se dice cuál para no revelar qué cuentas existen. */
    INVALID_CREDENTIALS,

    /** El correo no tiene formato de correo. */
    INVALID_EMAIL,

    /** Ya existe una cuenta con ese correo. */
    EMAIL_ALREADY_IN_USE,

    /** La contraseña no cumple el mínimo que exige el servicio. */
    WEAK_PASSWORD,

    /** La cuenta fue deshabilitada por un administrador. */
    USER_DISABLED,

    /** Demasiados intentos seguidos: el servicio bloquea temporalmente. */
    TOO_MANY_REQUESTS,

    /** Sin conexión a internet. */
    NETWORK,

    /** La app se compiló sin `google-services.json`: no hay servicio de cuentas. */
    NOT_CONFIGURED,

    UNKNOWN
}

/** Fallo de autenticación ya clasificado; es lo que viaja dentro de `Result.failure`. */
class AuthException(
    val error: AuthError,
    cause: Throwable? = null
) : Exception(error.name, cause)
