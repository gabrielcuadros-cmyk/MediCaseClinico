package com.example.medicaseclinico.data.user

import com.example.medicaseclinico.domain.model.Invitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvitationMapperTest {

    @Test
    fun `el correo se normaliza para que coincida con el de la cuenta`() {
        assertEquals("ana@prueba.test", InvitationMapper.documentIdFor("  Ana@Prueba.TEST "))
        assertEquals("", InvitationMapper.documentIdFor(null))
    }

    @Test
    fun `una invitacion se guarda y se lee igual`() {
        val invitation = Invitation(ownerId = "dueno", userId = "registro1")

        assertEquals(invitation, InvitationMapper.fromMap(InvitationMapper.toMap(invitation)))
    }

    @Test
    fun `sin dueno o sin registro no es una invitacion valida`() {
        assertNull(InvitationMapper.fromMap(mapOf("ownerId" to "dueno")))
        assertNull(InvitationMapper.fromMap(mapOf("ownerId" to " ", "userId" to "registro1")))
        assertNull(InvitationMapper.fromMap(emptyMap()))
    }
}
