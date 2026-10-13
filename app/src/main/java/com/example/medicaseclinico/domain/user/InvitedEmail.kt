package com.example.medicaseclinico.domain.user

import com.example.medicaseclinico.domain.model.AuthorizedUser

/**
 * true si [email] ya pertenece a alguien de la lista.
 *
 * Compara sin distinguir mayúsculas ni espacios sobrantes, que es como la
 * gente escribe el mismo correo dos veces sin darse cuenta. Un registro sin
 * correo no coincide con nada.
 */
fun isEmailAlreadyInvited(email: String, users: List<AuthorizedUser>): Boolean {
    val wanted = email.trim()
    if (wanted.isEmpty()) return false
    return users.any { it.email.trim().equals(wanted, ignoreCase = true) }
}
