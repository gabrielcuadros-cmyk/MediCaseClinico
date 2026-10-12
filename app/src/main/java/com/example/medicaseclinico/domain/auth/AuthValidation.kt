package com.example.medicaseclinico.domain.auth

/**
 * Comprobaciones de los formularios de cuenta que se pueden hacer sin red,
 * antes de molestar al servidor: así el usuario ve el error al instante y
 * junto al campo que debe corregir.
 */
object AuthValidation {

    /** Mínimo que promete el formulario de registro ("Mínimo 8 caracteres"). */
    const val MIN_PASSWORD_LENGTH = 8

    // Deliberadamente permisiva: algo@dominio.ext. La validez real del correo
    // la decide el servidor; aquí solo se cazan los despistes evidentes.
    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$")

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

    fun isValidNewPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH
}
