package com.example.medicaseclinico.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPermissionsTest {

    @Test
    fun `sin indicar nada no se concede ningun permiso`() {
        assertEquals(
            UserPermissions(
                monitorTemperature = false,
                viewLocation = false,
                viewAccessLogs = false,
                receiveCriticalAlerts = false,
                exportReports = false,
                manageUsers = false
            ),
            UserPermissions()
        )
    }

    @Test
    fun `el principal y el administrador secundario empiezan con todos los permisos`() {
        assertEquals(UserPermissions.ALL, UserPermissions.defaultsFor(UserRole.PRINCIPAL))
        assertEquals(UserPermissions.ALL, UserPermissions.defaultsFor(UserRole.SECONDARY_ADMIN))
    }

    @Test
    fun `un cuidador empieza vigilando pero sin administrar`() {
        val caregiver = UserPermissions.defaultsFor(UserRole.CAREGIVER)

        assertTrue(caregiver.monitorTemperature)
        assertTrue(caregiver.viewLocation)
        assertTrue(caregiver.receiveCriticalAlerts)
        assertFalse(caregiver.viewAccessLogs)
        assertFalse(caregiver.exportReports)
        assertFalse(caregiver.manageUsers)
    }

    @Test
    fun `un usuario nuevo toma los permisos de su rol`() {
        val user = AuthorizedUser(
            name = "Laura Gómez",
            email = "laura@ejemplo.com",
            phone = "+51987654321",
            role = UserRole.CAREGIVER
        )

        assertEquals(UserPermissions.defaultsFor(UserRole.CAREGIVER), user.permissions)
    }
}
