package com.example.medicaseclinico.domain.auth

import org.junit.Assert.assertEquals
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

    @Test
    fun `el nombre necesita al menos dos letras sin contar espacios`() {
        assertFalse(AuthValidation.isValidName(""))
        assertFalse(AuthValidation.isValidName("  a "))
        assertTrue(AuthValidation.isValidName("Ana"))
    }

    @Test
    fun `acepta telefonos escritos con espacios guiones y prefijo`() {
        assertTrue(AuthValidation.isValidPhone("+51 987 654 321"))
        assertTrue(AuthValidation.isValidPhone("987-654-321"))
        assertTrue(AuthValidation.isValidPhone("(054) 234567"))
    }

    @Test
    fun `rechaza telefonos con letras o con muy pocos o demasiados digitos`() {
        listOf("", "12345", "98765432a", "+51 987 654 321 000 000 0", "++51987654321", "987+654321")
            .forEach { phone -> assertFalse(phone, AuthValidation.isValidPhone(phone)) }
    }

    @Test
    fun `el telefono se guarda solo con digitos y su prefijo`() {
        assertEquals("+51987654321", AuthValidation.normalizePhone(" +51 987-654 321 "))
        assertEquals("054234567", AuthValidation.normalizePhone("(054) 234567"))
    }
}
