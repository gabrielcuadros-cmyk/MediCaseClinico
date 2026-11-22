package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.AlertType
import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.CaseAccess
import com.example.medicaseclinico.domain.model.CaseCapability
import com.example.medicaseclinico.domain.model.Invitation
import com.example.medicaseclinico.domain.model.NotificationPreferences
import com.example.medicaseclinico.domain.model.UserPermissions
import com.example.medicaseclinico.domain.model.UserRole
import com.example.medicaseclinico.domain.model.restrictedTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveCaseAccessUseCaseTest {

    private val resolve = ResolveCaseAccessUseCase()
    private val invitation = Invitation(ownerId = "dueno", userId = "registro1")
    private val caregiver = AuthorizedUser(
        id = "registro1",
        name = "Ana",
        email = "ana@prueba.test",
        phone = "",
        role = UserRole.CAREGIVER
    )

    @Test
    fun `sin invitacion la cuenta es duena`() {
        assertEquals(
            CaseAccess.Owner("yo"),
            resolve(uid = "yo", guestOf = null, invitation = null, authorizedUser = null)
        )
    }

    @Test
    fun `con invitacion vigente entra como invitada con su rol y permisos`() {
        val access = resolve(uid = "yo", guestOf = null, invitation = invitation, authorizedUser = caregiver)

        assertEquals(
            CaseAccess.Guest(
                ownerId = "dueno",
                userId = "registro1",
                role = UserRole.CAREGIVER,
                permissions = UserPermissions.defaultsFor(UserRole.CAREGIVER)
            ),
            access
        )
    }

    @Test
    fun `si el dueno borra la invitacion la cuenta queda revocada y no pasa a duena`() {
        assertEquals(
            CaseAccess.Revoked,
            resolve(uid = "yo", guestOf = "dueno", invitation = null, authorizedUser = null)
        )
    }

    @Test
    fun `una invitacion sin registro en la lista del dueno es acceso revocado`() {
        assertEquals(
            CaseAccess.Revoked,
            resolve(uid = "yo", guestOf = "dueno", invitation = invitation, authorizedUser = null)
        )
    }

    @Test
    fun `invitarse a uno mismo no convierte al dueno en invitado`() {
        val own = Invitation(ownerId = "yo", userId = "registro1")

        assertEquals(
            CaseAccess.Owner("yo"),
            resolve(uid = "yo", guestOf = null, invitation = own, authorizedUser = caregiver)
        )
    }

    @Test
    fun `el dueno puede todo y el revocado nada`() {
        val owner = CaseAccess.Owner("yo")

        assertTrue(CaseCapability.entries.all { owner.can(it) })
        assertTrue(CaseCapability.entries.none { CaseAccess.Revoked.can(it) })
        assertEquals("yo", owner.caseOwnerId)
        assertNull(CaseAccess.Revoked.caseOwnerId)
    }

    @Test
    fun `el invitado solo puede lo que dicen sus permisos y nunca controlar el estuche`() {
        val guest = resolve(uid = "yo", guestOf = null, invitation = invitation, authorizedUser = caregiver)

        assertTrue(guest.can(CaseCapability.VIEW_TEMPERATURE))
        assertTrue(guest.can(CaseCapability.VIEW_LOCATION))
        assertTrue(guest.can(CaseCapability.RECEIVE_ALERTS))
        assertFalse(guest.can(CaseCapability.VIEW_ACCESS_LOGS))
        assertFalse(guest.can(CaseCapability.EXPORT_REPORTS))
        assertFalse(guest.can(CaseCapability.MANAGE_USERS))
        assertFalse(guest.can(CaseCapability.CONTROL_CASE))
        assertEquals("dueno", guest.caseOwnerId)
    }

    @Test
    fun `un administrador secundario gestiona usuarios pero tampoco controla el estuche`() {
        val admin = caregiver.copy(
            role = UserRole.SECONDARY_ADMIN,
            permissions = UserPermissions.defaultsFor(UserRole.SECONDARY_ADMIN)
        )
        val guest = resolve(uid = "yo", guestOf = null, invitation = invitation, authorizedUser = admin)

        assertTrue(guest.can(CaseCapability.MANAGE_USERS))
        assertFalse(guest.can(CaseCapability.CONTROL_CASE))
    }

    @Test
    fun `cada alerta depende del permiso de su dato`() {
        val guest = resolve(uid = "yo", guestOf = null, invitation = invitation, authorizedUser = caregiver)

        assertTrue(guest.canSeeAlert(AlertType.TEMPERATURE))
        assertTrue(guest.canSeeAlert(AlertType.GEOFENCE))
        assertTrue(guest.canSeeAlert(AlertType.CONNECTION))
        assertFalse(guest.canSeeAlert(AlertType.SECURITY))
        assertFalse(guest.canSeeAlert(AlertType.ACCESS_GRANTED))
        assertFalse(CaseAccess.Revoked.canSeeAlert(AlertType.CONNECTION))
    }

    @Test
    fun `el dueno recibe las notificaciones segun sus preferencias`() {
        val prefs = NotificationPreferences(batteryAlertsEnabled = false)

        assertEquals(prefs, prefs.restrictedTo(CaseAccess.Owner("yo")))
        assertEquals(prefs, prefs.restrictedTo(null))
    }

    @Test
    fun `sin recibir alertas criticas el invitado no recibe ninguna notificacion`() {
        val noAlerts = caregiver.copy(
            permissions = caregiver.permissions.copy(receiveCriticalAlerts = false)
        )
        val guest = resolve(uid = "yo", guestOf = null, invitation = invitation, authorizedUser = noAlerts)

        val prefs = NotificationPreferences().restrictedTo(guest)

        assertFalse(prefs.temperatureAlertsEnabled)
        assertFalse(prefs.connectionAlertsEnabled)
        assertFalse(prefs.geofenceAlertsEnabled)
        assertFalse(prefs.securityAlertsEnabled)
        assertFalse(prefs.batteryAlertsEnabled)
    }

    @Test
    fun `el invitado no recibe notificaciones de datos que no puede ver`() {
        val guest = resolve(uid = "yo", guestOf = null, invitation = invitation, authorizedUser = caregiver)

        val prefs = NotificationPreferences().restrictedTo(guest)

        assertTrue(prefs.temperatureAlertsEnabled)
        assertTrue(prefs.geofenceAlertsEnabled)
        assertFalse(prefs.securityAlertsEnabled)
        assertTrue(NotificationPreferences().restrictedTo(CaseAccess.Revoked).let {
            !it.temperatureAlertsEnabled && !it.connectionAlertsEnabled
        })
    }
}
