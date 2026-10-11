package com.example.medicaseclinico

import android.app.Application
import com.example.medicaseclinico.di.AppContainer

/**
 * Punto de arranque del proceso. Crea el contenedor de dependencias y
 * activa la sincronización BLE → Room.
 */
class MediCaseApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.telemetrySynchronizer.start()
        container.caseDeviceRepository.startMonitoring()
    }
}