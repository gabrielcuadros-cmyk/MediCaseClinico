package com.example.medicaseclinico.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Comprueba las migraciones contra los esquemas que exporta Room, sin
 * necesitar un teléfono: si una entidad y su migración dejan de coincidir,
 * este test falla en cada compilación.
 */
class MigrationSchemaTest {

    private val schemaDirectory = File("schemas/${AppDatabase::class.java.name}")

    /** Sentencias CREATE de un esquema exportado, con el nombre real de la tabla. */
    private fun createStatements(version: Int): Map<String, List<String>> {
        val json = File(schemaDirectory, "$version.json").readText()
        val entityBlocks = json.substringAfter("\"entities\"").substringBefore("\"setupQueries\"")
            .split("\"tableName\": \"").drop(1)
        return entityBlocks.associate { block ->
            val tableName = block.substringBefore("\"")
            val statements = Regex("\"createSql\": \"(.*?)\"\\s*[,}\\n]")
                .findAll(block)
                .map { it.groupValues[1].replace("\${TABLE_NAME}", tableName) }
                .toList()
            tableName to statements
        }
    }

    // ------------------------------------------------------------------
    //  Migración 1 → 2
    // ------------------------------------------------------------------
    @Test
    fun `la migracion 1 a 2 crea alert_events igual que el esquema exportado`() {
        val expected = createStatements(version = 2).getValue("alert_events")

        assertEquals(expected.sorted(), MIGRATION_1_2_STATEMENTS.sorted())
    }

    @Test
    fun `la version 2 solo anade alert_events y no toca las lecturas termicas`() {
        val version1 = createStatements(version = 1)
        val version2 = createStatements(version = 2)

        assertEquals(setOf("alert_events"), version2.keys - version1.keys)
        assertEquals(version1.getValue("temperature_readings"), version2.getValue("temperature_readings"))
        assertTrue(version1.getValue("temperature_readings").isNotEmpty())
    }

    @Test
    fun `la migracion declara las versiones 1 y 2`() {
        assertEquals(1, MIGRATION_1_2.startVersion)
        assertEquals(2, MIGRATION_1_2.endVersion)
    }

    // ------------------------------------------------------------------
    //  Migración 2 → 3
    // ------------------------------------------------------------------
    @Test
    fun `la migracion 2 a 3 crea case_locations igual que el esquema exportado`() {
        val expected = createStatements(version = 3).getValue("case_locations")

        assertEquals(expected.sorted(), MIGRATION_2_3_STATEMENTS.sorted())
    }

    @Test
    fun `la version 3 solo anade case_locations y no toca lecturas ni alertas`() {
        val version2 = createStatements(version = 2)
        val version3 = createStatements(version = 3)

        assertEquals(setOf("case_locations"), version3.keys - version2.keys)
        assertEquals(version2.getValue("temperature_readings"), version3.getValue("temperature_readings"))
        assertEquals(version2.getValue("alert_events"), version3.getValue("alert_events"))
        assertTrue(version2.getValue("alert_events").isNotEmpty())
    }

    @Test
    fun `la migracion declara las versiones 2 y 3`() {
        assertEquals(2, MIGRATION_2_3.startVersion)
        assertEquals(3, MIGRATION_2_3.endVersion)
    }

    // ------------------------------------------------------------------
    //  Migración 3 → 4
    // ------------------------------------------------------------------
    @Test
    fun `la migracion 3 a 4 crea geofences igual que el esquema exportado`() {
        val expected = createStatements(version = 4).getValue("geofences")

        assertEquals(expected, listOf(MIGRATION_3_4_CREATE_GEOFENCES))
    }

    @Test
    fun `la migracion 3 a 4 anade detail al final de alert_events como en el esquema exportado`() {
        val version3Table = createStatements(version = 3).getValue("alert_events").first()
        val version4Table = createStatements(version = 4).getValue("alert_events").first()

        // ALTER TABLE ADD COLUMN siempre agrega la columna al final.
        val afterAlterTable = version3Table.removeSuffix(")") + ", $MIGRATION_3_4_DETAIL_COLUMN)"
        assertEquals(version4Table, afterAlterTable)
    }

    @Test
    fun `la version 4 solo anade geofences y no toca lecturas ni posiciones`() {
        val version3 = createStatements(version = 3)
        val version4 = createStatements(version = 4)

        assertEquals(setOf("geofences"), version4.keys - version3.keys)
        assertEquals(version3.getValue("temperature_readings"), version4.getValue("temperature_readings"))
        assertEquals(version3.getValue("case_locations"), version4.getValue("case_locations"))
        assertEquals(
            version3.getValue("alert_events").drop(1),
            version4.getValue("alert_events").drop(1)
        )
    }

    @Test
    fun `la migracion declara las versiones 3 y 4`() {
        assertEquals(3, MIGRATION_3_4.startVersion)
        assertEquals(4, MIGRATION_3_4.endVersion)
    }
}
