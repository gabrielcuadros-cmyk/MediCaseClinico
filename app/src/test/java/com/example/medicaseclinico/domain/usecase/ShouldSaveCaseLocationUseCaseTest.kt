package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.CaseLocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldSaveCaseLocationUseCaseTest {

    private val shouldSave = ShouldSaveCaseLocationUseCase(
        minIntervalMs = 5_000L,
        maxIntervalMs = 60_000L,
        minDistanceMeters = 25.0
    )

    // Plaza de Armas de Arequipa.
    private val latitude = -16.3989
    private val longitude = -71.5369
    private val savedAt = 1_000_000L
    private val lastSaved = CaseLocation(latitude = latitude, longitude = longitude, timestamp = savedAt)

    /** 0.0001 grados de latitud son unos 11 metros. */
    private fun metersNorth(meters: Double) = latitude + meters / 11.12 * 0.0001

    @Test
    fun `la primera posicion siempre se guarda`() {
        assertTrue(shouldSave(null, latitude, longitude, savedAt))
    }

    @Test
    fun `un estuche quieto no se guarda cada segundo`() {
        assertFalse(shouldSave(lastSaved, latitude, longitude, savedAt + 1_000))
        assertFalse(shouldSave(lastSaved, latitude, longitude, savedAt + 30_000))
    }

    @Test
    fun `el vaiven de un gps quieto no cuenta como movimiento`() {
        assertFalse(shouldSave(lastSaved, metersNorth(10.0), longitude, savedAt + 30_000))
    }

    @Test
    fun `un estuche quieto se vuelve a guardar al cumplirse el intervalo maximo`() {
        assertFalse(shouldSave(lastSaved, latitude, longitude, savedAt + 59_999))
        assertTrue(shouldSave(lastSaved, latitude, longitude, savedAt + 60_000))
    }

    @Test
    fun `un movimiento real se guarda pasado el intervalo minimo`() {
        assertTrue(shouldSave(lastSaved, metersNorth(30.0), longitude, savedAt + 5_000))
    }

    @Test
    fun `durante un trayecto no se guarda mas de una vez por intervalo minimo`() {
        assertFalse(shouldSave(lastSaved, metersNorth(30.0), longitude, savedAt + 4_999))
    }

    @Test
    fun `si el reloj del telefono retrocede la posicion se guarda`() {
        assertTrue(shouldSave(lastSaved, latitude, longitude, savedAt - 1))
    }
}
