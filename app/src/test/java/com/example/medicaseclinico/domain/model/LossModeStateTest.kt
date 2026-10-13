package com.example.medicaseclinico.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LossModeStateTest {

    @Test
    fun `por defecto el modo perdida esta desactivado y sin ayudas`() {
        val state = LossModeState()

        assertFalse(state.isActive)
        assertNull(state.activatedAt)
        assertFalse(state.alarmRequested)
        assertFalse(state.highFrequencyGpsRequested)
    }

    @Test
    fun `activar guarda la hora`() {
        val state = LossModeState().activated(now = 1_000L)

        assertTrue(state.isActive)
        assertEquals(1_000L, state.activatedAt)
    }

    @Test
    fun `activar dos veces conserva la hora original y las ayudas pedidas`() {
        val first = LossModeState().activated(now = 1_000L).withAlarm(true)

        assertEquals(first, first.activated(now = 9_000L))
    }

    @Test
    fun `las ayudas se piden por separado con el modo activo`() {
        val state = LossModeState().activated(now = 1_000L)
            .withAlarm(true)
            .withHighFrequencyGps(true)
            .withAlarm(false)

        assertFalse(state.alarmRequested)
        assertTrue(state.highFrequencyGpsRequested)
    }

    @Test
    fun `sin el modo activo no se pueden pedir ayudas`() {
        val state = LossModeState().withAlarm(true).withHighFrequencyGps(true)

        assertEquals(LossModeState(), state)
    }

    @Test
    fun `desactivar apaga tambien la alarma y el gps rapido`() {
        val state = LossModeState().activated(now = 1_000L)
            .withAlarm(true)
            .withHighFrequencyGps(true)
            .deactivated()

        assertEquals(LossModeState(), state)
    }
}
