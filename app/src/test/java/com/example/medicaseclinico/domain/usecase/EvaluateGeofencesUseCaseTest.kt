package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.Geofence
import com.example.medicaseclinico.domain.model.GeofenceTransition
import com.example.medicaseclinico.domain.model.GeofenceTransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateGeofencesUseCaseTest {

    private val evaluate = EvaluateGeofencesUseCase(hysteresisMeters = 20.0)

    // Plaza de Armas de Arequipa.
    private val latitude = -16.3989
    private val longitude = -71.5369

    private val home = Geofence(
        id = 1L,
        name = "Casa",
        latitude = latitude,
        longitude = longitude,
        radiusMeters = 150.0
    )

    /** 0.0001 grados de latitud son unos 11 metros. */
    private fun metersNorth(meters: Double) = latitude + meters / 11.12 * 0.0001

    /** Evalúa la zona [home] con el estuche a [meters] de su centro. */
    private fun evaluateAt(meters: Double, previous: Map<Long, Boolean> = emptyMap()) =
        evaluate(listOf(home), metersNorth(meters), longitude, previous)

    @Test
    fun `sin zonas no hay estado ni transiciones`() {
        val result = evaluate(emptyList(), latitude, longitude)

        assertTrue(result.insideById.isEmpty())
        assertTrue(result.transitions.isEmpty())
    }

    @Test
    fun `la primera evaluacion dentro de la zona no es una entrada`() {
        val result = evaluateAt(0.0)

        assertEquals(mapOf(1L to true), result.insideById)
        assertTrue(result.transitions.isEmpty())
    }

    @Test
    fun `la primera evaluacion fuera de la zona no es una salida`() {
        val result = evaluateAt(300.0)

        assertEquals(mapOf(1L to false), result.insideById)
        assertTrue(result.transitions.isEmpty())
    }

    @Test
    fun `la primera evaluacion usa el radio sin margen`() {
        assertEquals(mapOf(1L to true), evaluateAt(140.0).insideById)
        assertEquals(mapOf(1L to false), evaluateAt(160.0).insideById)
    }

    @Test
    fun `pasar el radio sin superar el margen no es una salida`() {
        val result = evaluateAt(160.0, previous = mapOf(1L to true))

        assertEquals(mapOf(1L to true), result.insideById)
        assertTrue(result.transitions.isEmpty())
    }

    @Test
    fun `alejarse mas alla del margen es una salida`() {
        val result = evaluateAt(180.0, previous = mapOf(1L to true))

        assertEquals(mapOf(1L to false), result.insideById)
        assertEquals(
            listOf(GeofenceTransition(geofence = home, type = GeofenceTransitionType.EXITED)),
            result.transitions
        )
    }

    @Test
    fun `volver dentro del radio sin superar el margen no es una entrada`() {
        val result = evaluateAt(140.0, previous = mapOf(1L to false))

        assertEquals(mapOf(1L to false), result.insideById)
        assertTrue(result.transitions.isEmpty())
    }

    @Test
    fun `meterse mas alla del margen es una entrada`() {
        val result = evaluateAt(120.0, previous = mapOf(1L to false))

        assertEquals(mapOf(1L to true), result.insideById)
        assertEquals(
            listOf(GeofenceTransition(geofence = home, type = GeofenceTransitionType.ENTERED)),
            result.transitions
        )
    }

    @Test
    fun `el vaiven del gps en el borde no genera transiciones`() {
        var state = evaluateAt(145.0).insideById

        listOf(155.0, 145.0, 160.0, 140.0, 165.0).forEach { meters ->
            val result = evaluateAt(meters, previous = state)
            assertTrue(result.transitions.isEmpty())
            state = result.insideById
        }
        assertEquals(mapOf(1L to true), state)
    }

    @Test
    fun `salir y volver genera una salida y luego una entrada`() {
        val inside = evaluateAt(0.0)
        val exited = evaluateAt(400.0, previous = inside.insideById)
        val stillOutside = evaluateAt(450.0, previous = exited.insideById)
        val entered = evaluateAt(50.0, previous = stillOutside.insideById)

        assertEquals(listOf(GeofenceTransitionType.EXITED), exited.transitions.map { it.type })
        assertTrue(stillOutside.transitions.isEmpty())
        assertEquals(listOf(GeofenceTransitionType.ENTERED), entered.transitions.map { it.type })
    }

    @Test
    fun `una zona inactiva no se evalua`() {
        val inactive = home.copy(isActive = false)

        val result = evaluate(listOf(inactive), metersNorth(400.0), longitude, mapOf(1L to true))

        assertTrue(result.insideById.isEmpty())
        assertTrue(result.transitions.isEmpty())
    }

    @Test
    fun `una zona reactivada con el estuche fuera no genera una salida`() {
        val whileInactive = evaluate(
            listOf(home.copy(isActive = false)),
            metersNorth(400.0),
            longitude,
            mapOf(1L to true)
        )

        val reactivated = evaluateAt(400.0, previous = whileInactive.insideById)

        assertEquals(mapOf(1L to false), reactivated.insideById)
        assertTrue(reactivated.transitions.isEmpty())
    }

    @Test
    fun `una zona borrada desaparece del estado`() {
        val result = evaluate(emptyList(), latitude, longitude, mapOf(1L to true))

        assertTrue(result.insideById.isEmpty())
        assertTrue(result.transitions.isEmpty())
    }

    @Test
    fun `cada zona se evalua por separado`() {
        // Centro a 300 m al norte de Casa y radio de 500 m: contiene a Casa entera.
        val clinic = Geofence(
            id = 2L,
            name = "Clínica",
            latitude = metersNorth(300.0),
            longitude = longitude,
            radiusMeters = 500.0
        )

        val result = evaluate(
            listOf(home, clinic),
            metersNorth(200.0),
            longitude,
            mapOf(1L to true, 2L to true)
        )

        assertEquals(mapOf(1L to false, 2L to true), result.insideById)
        assertEquals(
            listOf(GeofenceTransition(geofence = home, type = GeofenceTransitionType.EXITED)),
            result.transitions
        )
    }

    @Test
    fun `en una zona mas pequena que el margen se puede volver a entrar`() {
        val small = home.copy(radiusMeters = 30.0)

        // El margen se limita a la mitad del radio: se entra por debajo de 15 m.
        val stillOutside = evaluate(listOf(small), metersNorth(20.0), longitude, mapOf(1L to false))
        val entered = evaluate(listOf(small), metersNorth(10.0), longitude, mapOf(1L to false))

        assertEquals(mapOf(1L to false), stillOutside.insideById)
        assertEquals(mapOf(1L to true), entered.insideById)
    }
}
