package com.example.medicaseclinico.data.user

import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.UserPermissions
import com.example.medicaseclinico.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthorizedUserMapperTest {

    private val laura = AuthorizedUser(
        id = "abc",
        name = "Laura Gómez",
        email = "laura@ejemplo.com",
        phone = "+51987654321",
        role = UserRole.CAREGIVER,
        permissions = UserPermissions(monitorTemperature = true, exportReports = true),
        lastAccessAt = 1_700_000_000_000L
    )

    @Test
    fun `lo que se guarda se lee igual`() {
        val stored = AuthorizedUserMapper.toMap(laura)

        assertEquals(laura, AuthorizedUserMapper.fromMap(id = "abc", data = stored))
    }

    @Test
    fun `el id no se guarda dentro del documento`() {
        assertEquals(false, AuthorizedUserMapper.toMap(laura).containsKey("id"))
    }

    @Test
    fun `sin nombre o sin rol reconocible el documento no se lista`() {
        val stored = AuthorizedUserMapper.toMap(laura)

        assertNull(AuthorizedUserMapper.fromMap("abc", stored - "name"))
        assertNull(AuthorizedUserMapper.fromMap("abc", stored + ("name" to "  ")))
        assertNull(AuthorizedUserMapper.fromMap("abc", stored - "role"))
        assertNull(AuthorizedUserMapper.fromMap("abc", stored + ("role" to "SUPERUSUARIO")))
    }

    @Test
    fun `un documento sin permisos se lee sin ningun permiso concedido`() {
        val user = AuthorizedUserMapper.fromMap(
            id = "abc",
            data = mapOf("name" to "Carlos Ruiz", "role" to "SECONDARY_ADMIN")
        )

        assertEquals(UserPermissions(), user?.permissions)
        assertEquals("", user?.email)
        assertNull(user?.lastAccessAt)
    }

    @Test
    fun `un permiso que no es booleano no se concede`() {
        val user = AuthorizedUserMapper.fromMap(
            id = "abc",
            data = mapOf(
                "name" to "Carlos Ruiz",
                "role" to "CAREGIVER",
                "permissions" to mapOf("manageUsers" to "true", "viewLocation" to true)
            )
        )

        assertEquals(UserPermissions(viewLocation = true), user?.permissions)
    }

    @Test
    fun `la fecha de ultimo acceso se lee aunque firestore la devuelva como otro tipo numerico`() {
        val user = AuthorizedUserMapper.fromMap(
            id = "abc",
            data = mapOf("name" to "Carlos Ruiz", "role" to "CAREGIVER", "lastAccessAt" to 1_700_000_000_000.0)
        )

        assertEquals(1_700_000_000_000L, user?.lastAccessAt)
    }
}
