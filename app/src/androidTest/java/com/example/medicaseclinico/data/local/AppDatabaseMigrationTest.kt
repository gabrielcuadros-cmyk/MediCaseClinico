package com.example.medicaseclinico.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ejecuta la migración real sobre una base de datos en el dispositivo.
 *
 * Crea la base con el esquema de la versión 1, guarda una lectura, migra a la
 * versión 2 y deja que Room valide el resultado contra el esquema exportado.
 * Garantiza que un usuario que actualiza la app conserva su historial.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrar_de_1_a_2_conserva_las_lecturas_y_crea_alert_events() {
        helper.createDatabase(TEST_DATABASE, 1).use { database ->
            database.execSQL(
                "INSERT INTO temperature_readings " +
                    "(temperature_celsius, timestamp, is_alert_triggered, battery_level) " +
                    "VALUES (4.2, 1000, 0, 78)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DATABASE, 2, true, MIGRATION_1_2)

        migrated.query("SELECT temperature_celsius, battery_level FROM temperature_readings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(4.2f, cursor.getFloat(0), 0.001f)
            assertEquals(78, cursor.getInt(1))
            assertEquals(1, cursor.count)
        }
        migrated.query("SELECT COUNT(*) FROM alert_events").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun alert_events_acepta_una_alerta_tras_migrar() {
        helper.createDatabase(TEST_DATABASE, 1).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DATABASE, 2, true, MIGRATION_1_2)
        migrated.execSQL(
            "INSERT INTO alert_events (type, timestamp, numeric_value, is_read) " +
                "VALUES ('TEMPERATURE', 2000, 8.4, 0)"
        )

        migrated.query("SELECT type, numeric_value, is_read FROM alert_events").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("TEMPERATURE", cursor.getString(0))
            assertEquals(8.4f, cursor.getFloat(1), 0.001f)
            assertEquals(0, cursor.getInt(2))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-test.db"
    }
}
