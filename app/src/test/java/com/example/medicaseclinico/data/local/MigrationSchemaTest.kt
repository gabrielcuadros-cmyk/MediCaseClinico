package com.example.medicaseclinico.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Comprueba la migración 1 → 2 contra los esquemas que exporta Room, sin
 * necesitar un teléfono: si la entidad y la migración dejan de coincidir,
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
}
