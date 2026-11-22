package com.example.medicaseclinico

import android.app.Application
import com.example.medicaseclinico.data.notification.AlertChannel
import com.example.medicaseclinico.di.AppContainer

/**
 * Punto de arranque del proceso. Crea el contenedor de dependencias,
 * inicializa los canales de notificación y activa el monitoreo BLE + sincronización
 * y la actualización de los widgets de la pantalla de inicio.
 */
class MediCaseApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Los canales deben existir antes de que llegue la primera alerta.
        AlertChannel.createAll(this)

        container.telemetrySynchronizer.start()
        container.caseDeviceRepository.startMonitoring()
        container.caseWidgetRefresher.start()
        container.recentAlertsWidgetRefresher.start()
    }
}