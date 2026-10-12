package com.example.medicaseclinico.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoCircleTest {

    // Plaza de Armas de Arequipa.
    private val latitude = -16.3989
    private val longitude = -71.5369

    @Test
    fun `todos los vertices quedan a la distancia del radio`() {
        val outline = circleOutline(latitude, longitude, radiusMeters = 500.0)

        outline.forEach { vertex ->
            val distance = distanceMeters(latitude, longitude, vertex.latitude, vertex.longitude)
            assertEquals(500.0, distance, 0.01)
        }
    }

    @Test
    fun `el contorno es un poligono cerrado`() {
        val outline = circleOutline(latitude, longitude, radiusMeters = 150.0, vertexCount = 32)

        assertEquals(33, outline.size)
        assertEquals(outline.first(), outline.last())
    }

    @Test
    fun `el primer vertice queda al norte del centro`() {
        val north = circleOutline(latitude, longitude, radiusMeters = 150.0).first()

        assertTrue(north.latitude > latitude)
        assertEquals(longitude, north.longitude, 1e-9)
    }

    @Test
    fun `el contorno rodea al centro por los cuatro lados`() {
        val outline = circleOutline(latitude, longitude, radiusMeters = 150.0)

        assertTrue(outline.any { it.latitude > latitude })
        assertTrue(outline.any { it.latitude < latitude })
        assertTrue(outline.any { it.longitude > longitude })
        assertTrue(outline.any { it.longitude < longitude })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `menos de tres vertices no forman un poligono`() {
        circleOutline(latitude, longitude, radiusMeters = 150.0, vertexCount = 2)
    }
}
