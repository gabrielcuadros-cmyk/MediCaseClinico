package com.example.medicaseclinico.domain.user

private const val MAX_INITIALS = 2
private val WHITESPACE = Regex("\\s+")

/**
 * Iniciales de una persona: "Sofía Martínez" → "SM".
 *
 * Sustituyen a la foto del diseño, que no existe como dato. Está en un único
 * lugar para que todas las listas de usuarios dibujen el mismo avatar.
 *
 * @return hasta dos letras en mayúscula, o una cadena vacía si no hay nombre.
 */
fun initialsOf(name: String): String =
    name.trim()
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }
        .take(MAX_INITIALS)
        .joinToString(separator = "") { it.first().uppercase() }
