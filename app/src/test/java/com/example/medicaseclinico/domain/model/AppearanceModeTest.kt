package com.example.medicaseclinico.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceModeTest {

    @Test
    fun `con sistema la app sigue al telefono`() {
        assertTrue(AppearanceMode.SYSTEM.isDark(systemIsDark = true))
        assertFalse(AppearanceMode.SYSTEM.isDark(systemIsDark = false))
    }

    @Test
    fun `claro y oscuro mandan sobre el telefono`() {
        assertFalse(AppearanceMode.LIGHT.isDark(systemIsDark = true))
        assertTrue(AppearanceMode.DARK.isDark(systemIsDark = false))
    }

    @Test
    fun `lo guardado se lee igual`() {
        AppearanceMode.entries.forEach { mode ->
            assertEquals(mode, AppearanceMode.fromStored(mode.name))
        }
    }

    @Test
    fun `sin valor guardado o con uno desconocido se sigue al telefono`() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStored(null))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStored("SEPIA"))
    }
}
