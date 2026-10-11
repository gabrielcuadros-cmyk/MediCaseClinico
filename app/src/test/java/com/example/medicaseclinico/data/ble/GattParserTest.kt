package com.example.medicaseclinico.data.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifica la decodificación de las tramas estándar del Bluetooth SIG.
 */
class GattParserTest {

    private val delta = 0.001f

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // ------------------------------------------------------------------
    //  Temperatura (0x2A1C)
    // ------------------------------------------------------------------
    @Test
    fun `4_2 grados Celsius del diseno se decodifica correctamente`() {
        val payload = bytes(0x00, 0x2A, 0x00, 0x00, 0xFF)
        assertEquals(4.2f, GattParser.parseTemperatureCelsius(payload)!!, delta)
    }

    @Test
    fun `8_4 grados Celsius critico se decodifica correctamente`() {
        val payload = bytes(0x00, 0x54, 0x00, 0x00, 0xFF)
        assertEquals(8.4f, GattParser.parseTemperatureCelsius(payload)!!, delta)
    }

    @Test
    fun `temperatura negativa usa extension de signo de 24 bits`() {
        val payload = bytes(0x00, 0xE7, 0xFF, 0xFF, 0xFF)
        assertEquals(-2.5f, GattParser.parseTemperatureCelsius(payload)!!, delta)
    }

    @Test
    fun `lectura en Fahrenheit se convierte a Celsius`() {
        val payload = bytes(0x01, 0x88, 0x01, 0x00, 0xFF)
        assertEquals(4.0f, GattParser.parseTemperatureCelsius(payload)!!, delta)
    }

    @Test
    fun `trama con marca de tiempo adicional sigue leyendo la temperatura`() {
        val payload = bytes(
            0x02, 0x2A, 0x00, 0x00, 0xFF,
            0xEA, 0x07, 0x0A, 0x0A, 0x14, 0x1E, 0x00
        )
        assertEquals(4.2f, GattParser.parseTemperatureCelsius(payload)!!, delta)
    }

    @Test
    fun `valor especial NaN del sensor devuelve null`() {
        val payload = bytes(0x00, 0xFF, 0xFF, 0x7F, 0x00)
        assertNull(GattParser.parseTemperatureCelsius(payload))
    }

    @Test
    fun `trama incompleta devuelve null sin lanzar excepcion`() {
        assertNull(GattParser.parseTemperatureCelsius(bytes(0x00, 0x2A)))
        assertNull(GattParser.parseTemperatureCelsius(ByteArray(0)))
    }

    @Test
    fun `valor fuera del rango fisico del sensor se descarta`() {
        val payload = bytes(0x00, 0xD0, 0x07, 0x00, 0x00)
        assertNull(GattParser.parseTemperatureCelsius(payload))
    }

    // ------------------------------------------------------------------
    //  Batería (0x2A19)
    // ------------------------------------------------------------------
    @Test
    fun `bateria del 78 por ciento del diseno se decodifica`() {
        assertEquals(78, GattParser.parseBatteryPercent(bytes(78)))
    }

    @Test
    fun `bateria en los limites 0 y 100 es valida`() {
        assertEquals(0, GattParser.parseBatteryPercent(bytes(0)))
        assertEquals(100, GattParser.parseBatteryPercent(bytes(100)))
    }

    @Test
    fun `bateria con valor reservado mayor a 100 devuelve null`() {
        assertNull(GattParser.parseBatteryPercent(bytes(101)))
        assertNull(GattParser.parseBatteryPercent(bytes(0xFF)))
    }

    @Test
    fun `bateria sin datos devuelve null`() {
        assertNull(GattParser.parseBatteryPercent(ByteArray(0)))
    }
}