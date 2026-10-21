package com.example.medicaseclinico.data.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttConfigTest {

    private fun parse(
        host: String = "broker.ejemplo.test",
        port: String = "",
        useTls: String = "",
        topicPrefix: String = "Ana/prueba",
        username: String = "",
        password: String = "",
        caseId: String = ""
    ) = MqttConfig.parse(host, port, useTls, topicPrefix, username, password, caseId)

    @Test
    fun `sin host o sin prefijo no hay mqtt`() {
        assertNull(parse(host = ""))
        assertNull(parse(host = "   "))
        assertNull(parse(topicPrefix = ""))
        assertNull(parse(topicPrefix = "/"))
    }

    @Test
    fun `una copia limpia con todas las claves vacias no tiene mqtt`() {
        assertNull(MqttConfig.parse("", "", "", "", "", "", ""))
    }

    @Test
    fun `broker sin usuario ni id usa los topics directo bajo el prefijo`() {
        val config = parse(port = "1884", useTls = "false")

        assertNotNull(config)
        config!!
        assertEquals(1884, config.port)
        assertFalse(config.useTls)
        assertNull(config.username)
        assertNull(config.password)
        assertNull(config.caseId)
        assertEquals("Ana/prueba/temperatura", config.topic("temperatura"))
        assertEquals("Ana/prueba/orden/pin", config.topic("orden/pin"))
    }

    @Test
    fun `con id de estuche los topics quedan bajo prefijo e id`() {
        val config = parse(caseId = "estuche-01")!!

        assertEquals("Ana/prueba/estuche-01", config.topicBase)
        assertEquals("Ana/prueba/estuche-01/estado", config.topic("estado"))
    }

    @Test
    fun `el prefijo se limpia de espacios y barras sobrantes`() {
        assertEquals("Ana/prueba", parse(topicPrefix = " /Ana/prueba/ ")!!.topicPrefix)
    }

    @Test
    fun `puerto vacio usa el estandar segun tls`() {
        assertEquals(MqttConfig.DEFAULT_PORT, parse(useTls = "false")!!.port)
        assertEquals(MqttConfig.DEFAULT_TLS_PORT, parse(useTls = "TRUE")!!.port)
        assertTrue(parse(useTls = "true")!!.useTls)
    }

    @Test
    fun `valores invalidos desactivan mqtt en lugar de adivinar`() {
        assertNull(parse(port = "abc"))
        assertNull(parse(port = "0"))
        assertNull(parse(port = "70000"))
        assertNull(parse(useTls = "si"))
        assertNull(parse(host = "broker ejemplo"))
    }

    @Test
    fun `los comodines y niveles vacios no se aceptan`() {
        assertNull(parse(topicPrefix = "Ana/#"))
        assertNull(parse(topicPrefix = "Ana/+/prueba"))
        assertNull(parse(topicPrefix = "Ana//prueba"))
        assertNull(parse(caseId = "estuche/01"))
        assertNull(parse(caseId = "+"))
    }

    @Test
    fun `usuario y contrasena son opcionales`() {
        val config = parse(username = " ana ", password = "secreta")!!

        assertEquals("ana", config.username)
        assertEquals("secreta", config.password)
    }

    @Test
    fun `toString no muestra la contrasena`() {
        val text = parse(username = "ana", password = "secreta").toString()

        assertFalse(text.contains("secreta"))
        assertTrue(text.contains("***"))
    }
}
