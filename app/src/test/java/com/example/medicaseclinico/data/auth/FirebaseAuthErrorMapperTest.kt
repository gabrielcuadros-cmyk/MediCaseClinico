package com.example.medicaseclinico.data.auth

import com.example.medicaseclinico.domain.model.AuthError
import com.example.medicaseclinico.domain.model.AuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException

class FirebaseAuthErrorMapperTest {

    @Test
    fun `usuario inexistente y contrasena incorrecta son el mismo motivo`() {
        listOf(
            "ERROR_USER_NOT_FOUND",
            "ERROR_WRONG_PASSWORD",
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_INVALID_LOGIN_CREDENTIALS"
        ).forEach { code ->
            assertEquals(code, AuthError.INVALID_CREDENTIALS, authErrorFromCode(code))
        }
    }

    @Test
    fun `cada codigo conocido tiene su motivo`() {
        assertEquals(AuthError.INVALID_EMAIL, authErrorFromCode("ERROR_INVALID_EMAIL"))
        assertEquals(AuthError.EMAIL_ALREADY_IN_USE, authErrorFromCode("ERROR_EMAIL_ALREADY_IN_USE"))
        assertEquals(AuthError.WEAK_PASSWORD, authErrorFromCode("ERROR_WEAK_PASSWORD"))
        assertEquals(AuthError.USER_DISABLED, authErrorFromCode("ERROR_USER_DISABLED"))
        assertEquals(AuthError.TOO_MANY_REQUESTS, authErrorFromCode("ERROR_TOO_MANY_REQUESTS"))
        assertEquals(AuthError.NETWORK, authErrorFromCode("ERROR_NETWORK_REQUEST_FAILED"))
    }

    @Test
    fun `un codigo desconocido o ausente no rompe nada`() {
        assertEquals(AuthError.UNKNOWN, authErrorFromCode("ERROR_QUE_AUN_NO_EXISTE"))
        assertEquals(AuthError.UNKNOWN, authErrorFromCode(null))
    }

    @Test
    fun `un fallo que no es de firebase queda como desconocido y conserva la causa`() {
        val cause = IOException("disco lleno")

        val exception = cause.toAuthException()

        assertEquals(AuthError.UNKNOWN, exception.error)
        assertSame(cause, exception.cause)
    }

    @Test
    fun `un fallo ya clasificado no se vuelve a envolver`() {
        val classified = AuthException(AuthError.NOT_CONFIGURED)

        assertSame(classified, classified.toAuthException())
    }
}
