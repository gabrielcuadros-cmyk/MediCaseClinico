package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.Geofence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocateCaseInGeofencesUseCaseTest {

    private val locate = LocateCaseInGeofencesUseCase()

    // Plaza de Armas de Arequipa.
    private val latitude = -16.3989
    private val longitude = -71.5369

    /** 0.0001 grados de latitud son unos 11 metros. */
    private fun metersNorth(meters: Double) = latitude + meters / 11.12 * 0.0001

    private val home = Geofence(
        id = 1L,
        name = "Casa",
        latitude = latitude,
        longitude = longitude,
        radiusMeters = 150.0
    )

    // Centro a 1 km al norte de Casa.
    private val clinic = Geofence(
        id = 2L,
        name = "Clínica",
        latitude = metersNorth(1_000.0),
        longitude = longitude,
        radiusMeters = 500.0
    )

    @Test
    fun `sin zonas no hay nada que situar`() {
        assertTrue(locate(emptyList(), latitude, longitude).isEmpty())
    }

    @Test
    fun `el estuche dentro del radio esta dentro y con distancia negativa al borde`() {
        val presence = locate(listOf(home), metersNorth(50.0), longitude).single()

        assertTrue(presence.isInside)
        assertEquals(-100.0, presence.distanceToEdgeMeters, 1.0)
    }

    @Test
    fun `el estuche fuera del radio esta fuera y con distancia positiva al borde`() {
        val presence = locate(listOf(home), metersNorth(400.0), longitude).single()

        assertFalse(presence.isInside)
        assertEquals(250.0, presence.distanceToEdgeMeters, 1.0)
    }

    @Test
    fun `las zonas inactivas no se muestran`() {
        val result = locate(listOf(home.copy(isActive = false), clinic), latitude, longitude)

        assertEquals(listOf("Clínica"), result.map { it.geofence.name })
    }

    @Test
    fun `la zona que contiene al estuche va antes que una mas lejana`() {
        val result = locate(listOf(clinic, home), latitude, longitude)

        assertEquals(listOf("Casa", "Clínica"), result.map { it.geofence.name })
        assertEquals(listOf(true, false), result.map { it.isInside })
    }

    @Test
    fun `dentro de varias zonas va primero la mas pequena`() {
        // Misma posición central: el barrio contiene a Casa y al estuche.
        val district = home.copy(id = 3L, name = "Barrio", radiusMeters = 650.0)

        val result = locate(listOf(district, clinic, home), latitude, longitude)

        assertEquals(listOf("Casa", "Barrio", "Clínica"), result.map { it.geofence.name })
        assertEquals(listOf(true, true, false), result.map { it.isInside })
    }

    @Test
    fun `fuera de todas las zonas va primero la de borde mas cercano`() {
        // A 400 m de Casa: 250 m de su borde y 100 m del borde de Clínica.
        val result = locate(listOf(home, clinic), metersNorth(400.0), longitude)

        assertEquals(listOf("Clínica", "Casa"), result.map { it.geofence.name })
        assertEquals(listOf(false, false), result.map { it.isInside })
    }
}
