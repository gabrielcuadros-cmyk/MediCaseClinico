package com.example.medicaseclinico.data.ble

import com.example.medicaseclinico.domain.model.CaseGpsReading
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

    // ------------------------------------------------------------------
    //  Ubicación (0x2A67)
    // ------------------------------------------------------------------
    private val coordinateDelta = 1e-7

    private val flagSpeed = 0x0001
    private val flagDistance = 0x0002
    private val flagLocation = 0x0004
    private val statusOk = 1 shl 7
    private val statusEstimated = 2 shl 7
    private val statusLastKnown = 3 shl 7

    // Plaza de Armas de Lima, en unidades de 1e-7 grados.
    private val limaLatitude = -120_464_000
    private val limaLongitude = -770_428_000

    private fun littleEndian(value: Int, size: Int) =
        ByteArray(size) { index -> (value shr (8 * index)).toByte() }

    private fun locationPayload(
        flags: Int,
        latitude: Int = limaLatitude,
        longitude: Int = limaLongitude,
        prefix: ByteArray = ByteArray(0)
    ) = littleEndian(flags, 2) + prefix + littleEndian(latitude, 4) + littleEndian(longitude, 4)

    private fun assertLima(reading: CaseGpsReading?) {
        val fix = reading as CaseGpsReading.Fix
        assertEquals(-12.0464, fix.latitude, coordinateDelta)
        assertEquals(-77.0428, fix.longitude, coordinateDelta)
    }

    @Test
    fun `posicion valida se decodifica con latitud y longitud negativas`() {
        assertLima(GattParser.parseLocationAndSpeed(locationPayload(flagLocation or statusOk)))
    }

    @Test
    fun `posicion estimada tambien cuenta como posicion`() {
        assertLima(GattParser.parseLocationAndSpeed(locationPayload(flagLocation or statusEstimated)))
    }

    @Test
    fun `velocidad y distancia previas se saltan antes de leer la posicion`() {
        val payload = locationPayload(
            flags = flagSpeed or flagDistance or flagLocation or statusOk,
            prefix = bytes(0x10, 0x00, 0x20, 0x00, 0x00)
        )
        assertLima(GattParser.parseLocationAndSpeed(payload))
    }

    @Test
    fun `campos posteriores a la posicion no afectan la lectura`() {
        val flagElevation = 0x0008
        val payload = locationPayload(flagLocation or flagElevation or statusOk) +
            bytes(0x10, 0x27, 0x00)
        assertLima(GattParser.parseLocationAndSpeed(payload))
    }

    @Test
    fun `estado sin posicion es sin senal aunque la trama traiga coordenadas`() {
        assertEquals(
            CaseGpsReading.NoSignal,
            GattParser.parseLocationAndSpeed(locationPayload(flagLocation))
        )
    }

    @Test
    fun `ultima posicion conocida del gps se trata como sin senal`() {
        assertEquals(
            CaseGpsReading.NoSignal,
            GattParser.parseLocationAndSpeed(locationPayload(flagLocation or statusLastKnown))
        )
    }

    @Test
    fun `trama sin campo de posicion es sin senal`() {
        assertEquals(CaseGpsReading.NoSignal, GattParser.parseLocationAndSpeed(bytes(0x00, 0x00)))
        assertEquals(
            CaseGpsReading.NoSignal,
            GattParser.parseLocationAndSpeed(littleEndian(statusOk, 2))
        )
    }

    @Test
    fun `trama de ubicacion incompleta devuelve null sin lanzar excepcion`() {
        val complete = locationPayload(flagLocation or statusOk)

        assertNull(GattParser.parseLocationAndSpeed(ByteArray(0)))
        assertNull(GattParser.parseLocationAndSpeed(bytes(0x84)))
        assertNull(GattParser.parseLocationAndSpeed(complete.copyOf(complete.size - 1)))
    }

    @Test
    fun `coordenadas fuera del planeta se descartan`() {
        val tooFarNorth = locationPayload(flagLocation or statusOk, latitude = 900_000_001)
        val tooFarEast = locationPayload(flagLocation or statusOk, longitude = 1_800_000_001)

        assertNull(GattParser.parseLocationAndSpeed(tooFarNorth))
        assertNull(GattParser.parseLocationAndSpeed(tooFarEast))
    }

    @Test
    fun `los limites de latitud y longitud son validos`() {
        val payload = locationPayload(
            flags = flagLocation or statusOk,
            latitude = -900_000_000,
            longitude = 1_800_000_000
        )

        val fix = GattParser.parseLocationAndSpeed(payload) as CaseGpsReading.Fix
        assertEquals(-90.0, fix.latitude, coordinateDelta)
        assertEquals(180.0, fix.longitude, coordinateDelta)
    }
}