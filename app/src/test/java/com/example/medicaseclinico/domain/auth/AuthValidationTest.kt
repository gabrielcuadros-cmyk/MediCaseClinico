package com.example.medicaseclinico.domain.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {

    @Test
    fun `acepta correos con formato normal`() {
        assertTrue(AuthValidation.isValidEmail("ana@ejemplo.com"))
        assertTrue(AuthValidation.isValidEmail("ana.perez+insulina@clinica.edu.pe"))
    }

    @Test
    fun `ignora los espacios que se cuelan al escribir`() {
        assertTrue(AuthValidation.isValidEmail("  ana@ejemplo.com "))
    }

    @Test
    fun `rechaza correos incompletos o mal formados`() {
        listOf("", "ana", "ana@", "@ejemplo.com", "ana@ejemplo", "ana@ejemplo.", "ana@@ejemplo.com", "ana perez@ejemplo.com")
            .forEach { email -> assertFalse(email, AuthValidation.isValidEmail(email)) }
    }

    @Test
    fun `la contrasena nueva necesita el minimo de caracteres`() {
        assertFalse(AuthValidation.isValidNewPassword("1234567"))
        assertTrue(AuthValidation.isValidNewPassword("12345678"))
    }
}
