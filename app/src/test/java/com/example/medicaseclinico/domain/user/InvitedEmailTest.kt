package com.example.medicaseclinico.domain.user

import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitedEmailTest {

    private fun user(email: String) = AuthorizedUser(
        id = "id-$email",
        name = "Laura Gómez",
        email = email,
        phone = "+51987654321",
        role = UserRole.CAREGIVER
    )

    private val users = listOf(user("laura@ejemplo.com"), user("carlos@ejemplo.com"))

    @Test
    fun `un correo que ya esta en la lista se reconoce`() {
        assertTrue(isEmailAlreadyInvited("carlos@ejemplo.com", users))
    }

    @Test
    fun `no importan las mayusculas ni los espacios sobrantes`() {
        assertTrue(isEmailAlreadyInvited("  Laura@Ejemplo.COM ", users))
        assertTrue(isEmailAlreadyInvited("laura@ejemplo.com", listOf(user(" LAURA@ejemplo.com "))))
    }

    @Test
    fun `un correo nuevo no esta invitado`() {
        assertFalse(isEmailAlreadyInvited("sofia@ejemplo.com", users))
        assertFalse(isEmailAlreadyInvited("laura@ejemplo.com", emptyList()))
    }

    @Test
    fun `un registro sin correo no coincide con un correo vacio`() {
        assertFalse(isEmailAlreadyInvited("", listOf(user(""))))
        assertFalse(isEmailAlreadyInvited("   ", listOf(user(""))))
    }
}
