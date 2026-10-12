package com.example.medicaseclinico.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Sentencias de la migración 1 → 2: crea la tabla "alert_events" y su índice.
 *
 * Están en una lista aparte para que un test JVM pueda compararlas con el
 * esquema que exporta Room: si la entidad cambia y la migración no, el test
 * falla antes de que un usuario pierda acceso a su base de datos.
 */
internal val MIGRATION_1_2_STATEMENTS: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `alert_events` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`type` TEXT NOT NULL, " +
        "`timestamp` INTEGER NOT NULL, " +
        "`numeric_value` REAL, " +
        "`is_read` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_alert_events_timestamp` " +
        "ON `alert_events` (`timestamp`)"
)

/**
 * Añade el historial de alertas sin tocar "temperature_readings": las
 * lecturas térmicas ya guardadas en el teléfono se conservan intactas.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_1_2_STATEMENTS.forEach { statement -> db.execSQL(statement) }
    }
}

/** Sentencias de la migración 2 → 3: crea la tabla "case_locations" y su índice. */
internal val MIGRATION_2_3_STATEMENTS: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `case_locations` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`latitude` REAL NOT NULL, " +
        "`longitude` REAL NOT NULL, " +
        "`timestamp` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_case_locations_timestamp` " +
        "ON `case_locations` (`timestamp`)"
)

/**
 * Añade el historial de posiciones del estuche sin tocar las lecturas
 * térmicas ni las alertas ya guardadas.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_2_3_STATEMENTS.forEach { statement -> db.execSQL(statement) }
    }
}
