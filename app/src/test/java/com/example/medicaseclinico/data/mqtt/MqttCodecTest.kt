package com.example.medicaseclinico.data.mqtt

import com.example.medicaseclinico.domain.model.AccessEvent
import com.example.medicaseclinico.domain.model.AccessEventType
import com.example.medicaseclinico.domain.model.CaseCommandError
import com.example.medicaseclinico.domain.model.CaseGpsReading
import com.example.medicaseclinico.domain.model.LockState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttCodecTest {

    @Test
    fun `estado online y offline`() {
        assertEquals(true, MqttCodec.parseOnline("""{"online": true}"""))
        assertEquals(false, MqttCodec.parseOnline("""{"online": false}"""))
    }

    @Test
    fun `mensajes que no son json o con tipos inesperados se descartan`() {
        assertNull(MqttCodec.parseOnline("online"))
        assertNull(MqttCodec.parseOnline(""))
        assertNull(MqttCodec.parseOnline("""{"online": "true"}"""))
        assertNull(MqttCodec.parseOnline("[true]"))
        assertNull(MqttCodec.parseTemperatureCelsius("""{"celsius": "4.2"}"""))
    }

    @Test
    fun `temperatura dentro del rango del sensor`() {
        assertEquals(4.2f, MqttCodec.parseTemperatureCelsius("""{"celsius": 4.2}""")!!, 0.0001f)
        assertEquals(-1.5f, MqttCodec.parseTemperatureCelsius("""{"celsius": -1.5}""")!!, 0.0001f)
        assertEquals(8f, MqttCodec.parseTemperatureCelsius("""{"celsius": 8}""")!!, 0.0001f)
    }

    @Test
    fun `temperaturas imposibles del ds18b20 se descartan`() {
        assertNull(MqttCodec.parseTemperatureCelsius("""{"celsius": -127}"""))
        assertNull(MqttCodec.parseTemperatureCelsius("""{"celsius": 125.5}"""))
        assertNull(MqttCodec.parseTemperatureCelsius("""{"celsius": null}"""))
        assertNull(MqttCodec.parseTemperatureCelsius("{}"))
    }

    @Test
    fun `bateria entera de 0 a 100`() {
        assertEquals(78, MqttCodec.parseBatteryPercent("""{"porcentaje": 78}"""))
        assertEquals(0, MqttCodec.parseBatteryPercent("""{"porcentaje": 0}"""))
        assertNull(MqttCodec.parseBatteryPercent("""{"porcentaje": 101}"""))
        assertNull(MqttCodec.parseBatteryPercent("""{"porcentaje": 50.5}"""))
        assertNull(MqttCodec.parseBatteryPercent("""{"porcentaje": -1}"""))
    }

    @Test
    fun `ubicacion con fix`() {
        val reading = MqttCodec.parseLocation("""{"fix": true, "lat": -16.3989, "lng": -71.5369}""")

        assertEquals(CaseGpsReading.Fix(latitude = -16.3989, longitude = -71.5369), reading)
    }

    @Test
    fun `ubicacion sin fix es sin senal aunque traiga coordenadas`() {
        assertEquals(CaseGpsReading.NoSignal, MqttCodec.parseLocation("""{"fix": false}"""))
        assertEquals(
            CaseGpsReading.NoSignal,
            MqttCodec.parseLocation("""{"fix": false, "lat": 1.0, "lng": 2.0}""")
        )
    }

    @Test
    fun `ubicacion incompleta o fuera de rango no inventa una posicion`() {
        assertNull(MqttCodec.parseLocation("""{"fix": true, "lat": -16.3989}"""))
        assertNull(MqttCodec.parseLocation("""{"fix": true, "lat": 91, "lng": 0}"""))
        assertNull(MqttCodec.parseLocation("""{"fix": true, "lat": 0, "lng": -180.5}"""))
        assertNull(MqttCodec.parseLocation("""{"lat": 1.0, "lng": 2.0}"""))
    }

    @Test
    fun `cierre y seguro`() {
        assertEquals(
            MqttCodec.LockReport(lockState = LockState.LOCKED, pinRequired = true),
            MqttCodec.parseLock("""{"cierre": "cerrado", "seguro": true}""")
        )
        assertEquals(
            MqttCodec.LockReport(lockState = LockState.OPEN, pinRequired = null),
            MqttCodec.parseLock("""{"cierre": "abierto"}""")
        )
        assertEquals(
            MqttCodec.LockReport(lockState = null, pinRequired = false),
            MqttCodec.parseLock("""{"seguro": false}""")
        )
    }

    @Test
    fun `cierre con un valor desconocido se descarta entero`() {
        assertNull(MqttCodec.parseLock("""{"cierre": "entreabierto", "seguro": true}"""))
        assertNull(MqttCodec.parseLock("""{"cierre": "cerrado", "seguro": 1}"""))
        assertNull(MqttCodec.parseLock("{}"))
    }

    @Test
    fun `evento de acceso con la hora de recepcion`() {
        assertEquals(
            AccessEvent(type = AccessEventType.DENIED, failedAttempts = 2, receivedAt = 1_000L),
            MqttCodec.parseAccessEvent("""{"tipo": "denegado", "intentosFallidos": 2}""", 1_000L)
        )
        assertEquals(
            AccessEventType.LOCKED_OUT,
            MqttCodec.parseAccessEvent("""{"tipo": "bloqueo", "intentosFallidos": 3}""", 0L)?.type
        )
        assertEquals(
            AccessEventType.GRANTED,
            MqttCodec.parseAccessEvent("""{"tipo": "concedido", "intentosFallidos": 0}""", 0L)?.type
        )
    }

    @Test
    fun `evento de acceso invalido se descarta`() {
        assertNull(MqttCodec.parseAccessEvent("""{"tipo": "otro", "intentosFallidos": 0}""", 0L))
        assertNull(MqttCodec.parseAccessEvent("""{"tipo": "denegado"}""", 0L))
        assertNull(MqttCodec.parseAccessEvent("""{"tipo": "denegado", "intentosFallidos": 256}""", 0L))
    }

    @Test
    fun `respuesta correcta y errores conocidos`() {
        assertEquals(
            MqttCodec.CommandReply(commandId = "a1", error = null),
            MqttCodec.parseCommandReply("""{"id": "a1", "ok": true}""")
        )
        assertEquals(
            CaseCommandError.WRONG_PIN,
            MqttCodec.parseCommandReply("""{"id": "a1", "ok": false, "error": "pin_incorrecto"}""")?.error
        )
        assertEquals(
            CaseCommandError.LOCKED_OUT,
            MqttCodec.parseCommandReply("""{"id": "a1", "ok": false, "error": "bloqueado"}""")?.error
        )
        assertEquals(
            CaseCommandError.INVALID_VALUE,
            MqttCodec.parseCommandReply("""{"id": "a1", "ok": false, "error": "valor_invalido"}""")?.error
        )
        assertEquals(
            CaseCommandError.SECURE_CONNECTION_REQUIRED,
            MqttCodec.parseCommandReply("""{"id": "a1", "ok": false, "error": "requiere_tls"}""")?.error
        )
    }

    @Test
    fun `respuesta con error desconocido o sin error cuenta como fallo`() {
        assertEquals(
            CaseCommandError.FAILED,
            MqttCodec.parseCommandReply("""{"id": "a1", "ok": false, "error": "otro"}""")?.error
        )
        assertEquals(
            CaseCommandError.FAILED,
            MqttCodec.parseCommandReply("""{"id": "a1", "ok": false}""")?.error
        )
    }

    @Test
    fun `respuesta sin id o sin ok se descarta`() {
        assertNull(MqttCodec.parseCommandReply("""{"ok": true}"""))
        assertNull(MqttCodec.parseCommandReply("""{"id": " ", "ok": true}"""))
        assertNull(MqttCodec.parseCommandReply("""{"id": "a1"}"""))
    }

    @Test
    fun `orden de cambio de pin`() {
        val json = JSONObject(MqttCodec.encodePinChange("a1", "1234", "8052")!!)

        assertEquals("a1", json.getString("id"))
        assertEquals("1234", json.getString("pinActual"))
        assertEquals("8052", json.getString("pinNuevo"))
    }

    @Test
    fun `orden de seguro`() {
        val json = JSONObject(MqttCodec.encodePinRequired("a1", required = false, currentPin = "1234")!!)

        assertEquals("a1", json.getString("id"))
        assertFalse(json.getBoolean("activar"))
        assertEquals("1234", json.getString("pinActual"))
    }

    @Test
    fun `ordenes con pin invalido no se codifican`() {
        assertNull(MqttCodec.encodePinChange("a1", "123", "8052"))
        assertNull(MqttCodec.encodePinChange("a1", "1234", "80a2"))
        assertNull(MqttCodec.encodePinRequired("a1", required = true, currentPin = "12345"))
    }

    @Test
    fun `ordenes de alarma y gps`() {
        val alarm = JSONObject(MqttCodec.encodeAlarm("a1", on = true))
        assertEquals("a1", alarm.getString("id"))
        assertTrue(alarm.getBoolean("encender"))

        val gps = JSONObject(MqttCodec.encodeGpsInterval("a2", seconds = 1)!!)
        assertEquals("a2", gps.getString("id"))
        assertEquals(1, gps.getInt("segundos"))

        assertNull(MqttCodec.encodeGpsInterval("a3", seconds = 0))
        assertNull(MqttCodec.encodeGpsInterval("a3", seconds = 61))
    }
}
