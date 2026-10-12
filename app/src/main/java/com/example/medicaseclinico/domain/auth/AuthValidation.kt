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

    /** Un nombre de una sola letra es casi seguro un despiste. */
    const val MIN_NAME_LENGTH = 2

    // Rango de la norma internacional E.164 para un número con prefijo de país.
    private const val MIN_PHONE_DIGITS = 7
    private const val MAX_PHONE_DIGITS = 15
    private val PHONE_ALLOWED_REGEX = Regex("^\\+?[0-9 ()-]+$")

    fun isValidName(name: String): Boolean = name.trim().length >= MIN_NAME_LENGTH

    /**
     * Acepta el teléfono como la gente lo escribe ("+51 987 654 321",
     * "987-654-321"): solo importa que tenga una cantidad razonable de dígitos.
     */
    fun isValidPhone(phone: String): Boolean {
        val trimmed = phone.trim()
        if (!PHONE_ALLOWED_REGEX.matches(trimmed)) return false
        return trimmed.count { it.isDigit() } in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS
    }

    /** Deja el teléfono en una sola forma para guardarlo: dígitos y, si lo traía, el "+". */
    fun normalizePhone(phone: String): String {
        val trimmed = phone.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }
}
