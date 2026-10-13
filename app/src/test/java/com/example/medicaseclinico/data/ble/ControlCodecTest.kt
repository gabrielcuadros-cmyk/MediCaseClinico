package com.example.medicaseclinico.data.ble

import com.example.medicaseclinico.domain.model.AccessEventType
import com.example.medicaseclinico.domain.model.CaseCommandError
import com.example.medicaseclinico.domain.model.LockState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los ejemplos de bytes son los de hardware/ble-protocol.md. */
class ControlCodecTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // ------------------------------------------------------------------
    //  Estado del cierre y del seguro
    // ------------------------------------------------------------------
    @Test
    fun `el estado del cierre distingue cerrado y abierto`() {
        assertEquals(LockState.LOCKED, ControlCodec.parseLockState(bytes(0x00)))
        assertEquals(LockState.OPEN, ControlCodec.parseLockState(bytes(0x01)))
    }

    @Test
    fun `un estado de cierre desconocido o vacio no se interpreta`() {
        assertNull(ControlCodec.parseLockState(bytes(0x02)))
        assertNull(ControlCodec.parseLockState(bytes()))
    }

    @Test
    fun `el seguro distingue activado y desactivado`() {
        assertEquals(false, ControlCodec.parsePinRequired(bytes(0x00)))
        assertEquals(true, ControlCodec.parsePinRequired(bytes(0x01)))
        assertNull(ControlCodec.parsePinRequired(bytes(0x7F)))
        assertNull(ControlCodec.parsePinRequired(bytes()))
    }

    // ------------------------------------------------------------------
    //  Eventos de acceso
    // ------------------------------------------------------------------
    @Test
    fun `cada tipo de evento de acceso se reconoce con su contador de fallos`() {
        assertEquals(
            ControlCodec.AccessEventFrame(AccessEventType.GRANTED, 0),
            ControlCodec.parseAccessEvent(bytes(0x01, 0x00))
        )
        assertEquals(
            ControlCodec.AccessEventFrame(AccessEventType.DENIED, 2),
            ControlCodec.parseAccessEvent(bytes(0x02, 0x02))
        )
        assertEquals(
            ControlCodec.AccessEventFrame(AccessEventType.LOCKED_OUT, 3),
            ControlCodec.parseAccessEvent(bytes(0x03, 0x03))
        )
    }

    @Test
    fun `el contador de fallos es un byte sin signo`() {
        assertEquals(200, ControlCodec.parseAccessEvent(bytes(0x02, 200))?.failedAttempts)
    }

    @Test
    fun `un evento incompleto o de tipo desconocido se descarta`() {
        assertNull(ControlCodec.parseAccessEvent(bytes(0x02)))
        assertNull(ControlCodec.parseAccessEvent(bytes(0x00, 0x00)))
        assertNull(ControlCodec.parseAccessEvent(bytes(0x09, 0x01)))
    }

    // ------------------------------------------------------------------
    //  Órdenes
    // ------------------------------------------------------------------
    @Test
    fun `desactivar el seguro lleva el estado y el pin actual en ascii`() {
        assertArrayEquals(
            bytes(0x00, 0x31, 0x32, 0x33, 0x34),
            ControlCodec.encodePinRequired(required = false, currentPin = "1234")
        )
        assertArrayEquals(
            bytes(0x01, 0x30, 0x30, 0x30, 0x39),
            ControlCodec.encodePinRequired(required = true, currentPin = "0009")
        )
    }

    @Test
    fun `cambiar el pin lleva el actual seguido del nuevo`() {
        assertArrayEquals(
            bytes(0x31, 0x32, 0x33, 0x34, 0x38, 0x30, 0x35, 0x32),
            ControlCodec.encodePinChange(currentPin = "1234", newPin = "8052")
        )
    }

    @Test
    fun `un pin que no son cuatro digitos no se envia`() {
        listOf("", "123", "12345", "12a4", "12 4", "١٢٣٤").forEach { pin ->
            assertNull(pin, ControlCodec.encodePinRequired(required = true, currentPin = pin))
            assertNull(pin, ControlCodec.encodePinChange(currentPin = pin, newPin = "1234"))
            assertNull(pin, ControlCodec.encodePinChange(currentPin = "1234", newPin = pin))
        }
    }

    @Test
    fun `la alarma es un byte de encendido o apagado`() {
        assertArrayEquals(bytes(0x01), ControlCodec.encodeAlarm(on = true))
        assertArrayEquals(bytes(0x00), ControlCodec.encodeAlarm(on = false))
    }

    @Test
    fun `el intervalo del gps solo admite de 1 a 60 segundos`() {
        assertArrayEquals(bytes(1), ControlCodec.encodeGpsInterval(1))
        assertArrayEquals(bytes(60), ControlCodec.encodeGpsInterval(60))
        assertNull(ControlCodec.encodeGpsInterval(0))
        assertNull(ControlCodec.encodeGpsInterval(61))
        assertNull(ControlCodec.encodeGpsInterval(-5))
    }

    // ------------------------------------------------------------------
    //  Respuestas del estuche
    // ------------------------------------------------------------------
    @Test
    fun `una escritura aceptada no tiene error`() {
        assertNull(ControlCodec.errorFromWriteStatus(0x00))
    }

    @Test
    fun `los errores propios del estuche tienen su motivo`() {
        assertEquals(CaseCommandError.WRONG_PIN, ControlCodec.errorFromWriteStatus(0x80))
        assertEquals(CaseCommandError.INVALID_VALUE, ControlCodec.errorFromWriteStatus(0x81))
        assertEquals(CaseCommandError.LOCKED_OUT, ControlCodec.errorFromWriteStatus(0x82))
    }

    @Test
    fun `la falta de emparejamiento se reconoce en todas sus variantes`() {
        listOf(0x05, 0x08, 0x0F, 0x89).forEach { status ->
            assertEquals(CaseCommandError.PAIRING_REQUIRED, ControlCodec.errorFromWriteStatus(status))
        }
    }

    @Test
    fun `cualquier otro codigo es un fallo generico`() {
        assertEquals(CaseCommandError.FAILED, ControlCodec.errorFromWriteStatus(0x85))
        assertEquals(CaseCommandError.FAILED, ControlCodec.errorFromWriteStatus(257))
    }
}
