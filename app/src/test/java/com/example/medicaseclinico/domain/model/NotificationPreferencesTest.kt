package com.example.medicaseclinico.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreferencesTest {
    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `horario 22 a 07 cruza la medianoche correctamente`() {
        val prefs = NotificationPreferences(quietHoursEnabled = true)
        assertTrue(prefs.isWithinQuietHours(at(23)))
        assertTrue(prefs.isWithinQuietHours(at(2)))
        assertTrue(prefs.isWithinQuietHours(at(22)))
        assertFalse(prefs.isWithinQuietHours(at(7)))
        assertFalse(prefs.isWithinQuietHours(at(12)))
    }

    @Test
    fun `la temperatura suena aunque este silenciado`() {
        val prefs = NotificationPreferences(temporarilySilenced = true)
        assertTrue(prefs.allows(AlertType.TEMPERATURE, at(3)))
        assertFalse(prefs.allows(AlertType.BATTERY, at(3)))
    }

    @Test
    fun `en horario silencioso solo pasa la temperatura`() {
        val prefs = NotificationPreferences(quietHoursEnabled = true)
        assertTrue(prefs.allows(AlertType.TEMPERATURE, at(23)))
        assertFalse(prefs.allows(AlertType.CONNECTION, at(23)))
        assertTrue(prefs.allows(AlertType.CONNECTION, at(15)))
    }

    @Test
    fun `un tipo desactivado nunca se muestra, ni siquiera temperatura`() {
        val prefs = NotificationPreferences(temperatureAlertsEnabled = false)
        assertFalse(prefs.allows(AlertType.TEMPERATURE, at(12)))
    }
}