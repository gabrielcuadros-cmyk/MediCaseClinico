package com.example.medicaseclinico.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertFilterTest {

    private fun typesShownBy(filter: AlertFilter) = AlertType.entries.filter { filter.matches(it) }

    @Test
    fun `todas muestra cualquier tipo de alerta`() {
        assertEquals(AlertType.entries, typesShownBy(AlertFilter.ALL))
    }

    @Test
    fun `temperatura solo muestra alertas de temperatura`() {
        assertEquals(listOf(AlertType.TEMPERATURE), typesShownBy(AlertFilter.TEMPERATURE))
    }

    @Test
    fun `ubicacion solo muestra alertas de geocerca`() {
        assertEquals(listOf(AlertType.GEOFENCE), typesShownBy(AlertFilter.LOCATION))
    }

    @Test
    fun `seguridad solo muestra alertas de seguridad`() {
        assertEquals(listOf(AlertType.SECURITY), typesShownBy(AlertFilter.SECURITY))
    }

    @Test
    fun `bateria y bluetooth solo aparecen en todas`() {
        val specificFilters = AlertFilter.entries - AlertFilter.ALL

        listOf(AlertType.BATTERY, AlertType.CONNECTION).forEach { type ->
            assertEquals(emptyList<AlertFilter>(), specificFilters.filter { it.matches(type) })
        }
    }
}
