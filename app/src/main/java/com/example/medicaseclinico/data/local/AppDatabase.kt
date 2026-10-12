package com.example.medicaseclinico.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.medicaseclinico.data.local.dao.AlertEventDao
import com.example.medicaseclinico.data.local.dao.CaseLocationDao
import com.example.medicaseclinico.data.local.dao.GeofenceDao
import com.example.medicaseclinico.data.local.dao.TemperatureDao
import com.example.medicaseclinico.data.local.entity.AlertEventEntity
import com.example.medicaseclinico.data.local.entity.CaseLocationEntity
import com.example.medicaseclinico.data.local.entity.GeofenceEntity
import com.example.medicaseclinico.data.local.entity.TemperatureEntity

/**
 * Base de datos local de MediCase Clínico.
 *
 * Almacena el historial térmico del estuche, las alertas emitidas, las
 * posiciones de su GPS y las zonas de seguridad para que la app funcione aunque no haya red ni enlace
 * con el estuche: el Dashboard, el Historial, las Alertas y el Mapa leen
 * siempre de aquí (fuente única de verdad), y lo que llega por BLE se escribe
 * aquí.
 *
 * exportSchema = true → Room escribe el esquema JSON en app/schemas/
 * para versionar futuras migraciones.
 *
 * Cada cambio de esquema sube [version] y añade una Migration explícita en
 * Migrations.kt: nunca se borra la base del usuario para actualizar.
 */
@Database(
    entities = [
        TemperatureEntity::class,
        AlertEventEntity::class,
        CaseLocationEntity::class,
        GeofenceEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun temperatureDao(): TemperatureDao

    abstract fun alertEventDao(): AlertEventDao

    abstract fun caseLocationDao(): CaseLocationDao

    abstract fun geofenceDao(): GeofenceDao

    companion object {
        private const val DATABASE_NAME = "medicase_clinico.db"

        /**
         * @Volatile garantiza que todos los hilos vean la misma instancia
         * en cuanto se crea.
         */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Devuelve la instancia única (Singleton) de la base de datos.
         * Abrir varias instancias de Room consume memoria y puede causar
         * conflictos de escritura, por eso se sincroniza la creación.
         *
         * Se usa applicationContext para no retener una Activity y evitar
         * fugas de memoria.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): AppDatabase =
            Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}