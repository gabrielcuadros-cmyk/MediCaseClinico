package com.example.medicaseclinico.data.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MqttClientIdTest {

    @Test
    fun `el id de la app lleva el prefijo y un uuid`() {
        val uuid = UUID.fromString("0b6f2c1e-8d3a-4f5b-9c7e-1a2b3c4d5e6f")

        assertEquals(
            "medicase-app-0b6f2c1e-8d3a-4f5b-9c7e-1a2b3c4d5e6f",
            MqttClientId.newAppClientId(uuid)
        )
    }

    @Test
    fun `cada id nuevo es distinto`() {
        val ids = List(100) { MqttClientId.newAppClientId() }

        assertEquals(100, ids.toSet().size)
        assertTrue(ids.all { MqttClientId.isValidAppClientId(it) })
    }

    @Test
    fun `el id de la app nunca coincide con el del estuche`() {
        val appId = MqttClientId.newAppClientId()

        assertFalse(appId.startsWith("medicase-esp32-"))
        assertFalse(MqttClientId.isValidAppClientId("medicase-esp32-a4cf12b3c4d5"))
        assertNotEquals(appId, "medicase-esp32-a4cf12b3c4d5")
    }

    @Test
    fun `un id guardado danado o escrito a mano no es valido`() {
        assertFalse(MqttClientId.isValidAppClientId(""))
        assertFalse(MqttClientId.isValidAppClientId("medicase-app-"))
        assertFalse(MqttClientId.isValidAppClientId("medicase-app-1234"))
        assertFalse(MqttClientId.isValidAppClientId("medicase-app-1-1-1-1-1"))
        assertFalse(MqttClientId.isValidAppClientId("otra-app-0b6f2c1e-8d3a-4f5b-9c7e-1a2b3c4d5e6f"))
        assertFalse(MqttClientId.isValidAppClientId("medicase-app-0B6F2C1E-8D3A-4F5B-9C7E-1A2B3C4D5E6F"))
    }
}
