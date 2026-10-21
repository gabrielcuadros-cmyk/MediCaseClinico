package com.example.medicaseclinico.data.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class MqttClientIdTest {

    @Test
    fun `el id de la app lleva el prefijo y 16 digitos hex`() {
        val id = MqttClientId.newAppClientId(Random(42))

        assertTrue(id.startsWith("mcapp"))
        assertEquals(21, id.length)
        assertTrue(id.removePrefix("mcapp").all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `cabe en el limite que cualquier broker mqtt 3_1_1 debe aceptar`() {
        repeat(100) {
            val id = MqttClientId.newAppClientId()

            assertTrue(id.length <= MqttClientId.MAX_PORTABLE_LENGTH)
            assertTrue(id.all { it.isLetterOrDigit() && it.code < 128 })
        }
    }

    @Test
    fun `la misma semilla da el mismo id y semillas distintas no`() {
        assertEquals(
            MqttClientId.newAppClientId(Random(7)),
            MqttClientId.newAppClientId(Random(7))
        )
        assertFalse(
            MqttClientId.newAppClientId(Random(7)) == MqttClientId.newAppClientId(Random(8))
        )
    }

    @Test
    fun `cada id nuevo es distinto`() {
        val ids = List(1_000) { MqttClientId.newAppClientId() }

        assertEquals(1_000, ids.toSet().size)
        assertTrue(ids.all { MqttClientId.isValidAppClientId(it) })
    }

    @Test
    fun `el id de la app nunca coincide con el del estuche`() {
        assertFalse(MqttClientId.newAppClientId().startsWith("mcesp32"))
        assertFalse(MqttClientId.isValidAppClientId("mcesp32a4cf12b3c4d5"))
    }

    @Test
    fun `un id guardado danado o del formato largo anterior no es valido`() {
        assertFalse(MqttClientId.isValidAppClientId(""))
        assertFalse(MqttClientId.isValidAppClientId("mcapp"))
        assertFalse(MqttClientId.isValidAppClientId("mcapp0123456789abcde"))
        assertFalse(MqttClientId.isValidAppClientId("mcapp0123456789abcdef0"))
        assertFalse(MqttClientId.isValidAppClientId("mcapp0123456789ABCDEF"))
        assertFalse(MqttClientId.isValidAppClientId("mcapp0123456789abcdeg"))
        assertFalse(
            MqttClientId.isValidAppClientId("medicase-app-0b6f2c1e-8d3a-4f5b-9c7e-1a2b3c4d5e6f")
        )
    }
}
