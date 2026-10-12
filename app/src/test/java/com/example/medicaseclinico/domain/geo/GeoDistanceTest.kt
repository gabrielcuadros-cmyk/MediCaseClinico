package com.example.medicaseclinico.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDistanceTest {

    // Plaza de Armas de Arequipa y de Lima.
    private val arequipaLat = -16.3989
    private val arequipaLng = -71.5369
    private val limaLat = -12.0464
    private val limaLng = -77.0428

    @Test
    fun `la distancia de un punto a si mismo es cero`() {
        assertEquals(0.0, distanceMeters(arequipaLat, arequipaLng, arequipaLat, arequipaLng), 1e-6)
    }

    @Test
    fun `un grado de latitud mide unos 111 kilometros`() {
        assertEquals(111_194.9, distanceMeters(0.0, 0.0, 1.0, 0.0), 0.5)
    }

    @Test
    fun `un grado de longitud se acorta al alejarse del ecuador`() {
        val atEquator = distanceMeters(0.0, 0.0, 0.0, 1.0)
        val atArequipa = distanceMeters(arequipaLat, 0.0, arequipaLat, 1.0)

        assertEquals(111_194.9, atEquator, 0.5)
        assertEquals(atEquator * Math.cos(Math.toRadians(arequipaLat)), atArequipa, 5.0)
    }

    @Test
    fun `distancias de pocos metros se miden con precision`() {
        // 0.0001 grados de latitud son unos 11 metros.
        val meters = distanceMeters(arequipaLat, arequipaLng, arequipaLat + 0.0001, arequipaLng)

        assertEquals(11.12, meters, 0.01)
    }

    @Test
    fun `la distancia es la misma en ambos sentidos`() {
        val goingNorth = distanceMeters(arequipaLat, arequipaLng, limaLat, limaLng)
        val goingSouth = distanceMeters(limaLat, limaLng, arequipaLat, arequipaLng)

        assertEquals(goingNorth, goingSouth, 1e-6)
    }

    @Test
    fun `de Arequipa a Lima hay unos 765 kilometros en linea recta`() {
        val meters = distanceMeters(arequipaLat, arequipaLng, limaLat, limaLng)

        assertEquals(765_000.0, meters, 5_000.0)
    }

    @Test
    fun `cruzar el meridiano 180 no da la vuelta al mundo`() {
        assertEquals(22_238.9, distanceMeters(0.0, 179.9, 0.0, -179.9), 1.0)
    }
}
