package com.example.medicaseclinico.di

import android.content.Context
import com.example.medicaseclinico.data.ble.AndroidCaseDeviceRepository
import com.example.medicaseclinico.data.local.AppDatabase
import com.example.medicaseclinico.data.notification.SystemAlertPublisher
import com.example.medicaseclinico.data.preferences.DataStoreNotificationPreferencesRepository
import com.example.medicaseclinico.data.report.PdfMedicalReportExporter
import com.example.medicaseclinico.data.repository.AlertEventRepositoryImpl
import com.example.medicaseclinico.data.repository.CaseLocationRepositoryImpl
import com.example.medicaseclinico.data.repository.GeofenceRepositoryImpl
import com.example.medicaseclinico.data.repository.TemperatureRepositoryImpl
import com.example.medicaseclinico.data.sync.CaseTelemetrySynchronizer
import com.example.medicaseclinico.domain.alert.AlertPublisher
import com.example.medicaseclinico.domain.alert.RecordingAlertPublisher
import com.example.medicaseclinico.domain.model.NotificationPreferences
import com.example.medicaseclinico.domain.repository.AlertEventRepository
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.repository.CaseLocationRepository
import com.example.medicaseclinico.domain.repository.GeofenceRepository
import com.example.medicaseclinico.domain.repository.MedicalReportExporter
import com.example.medicaseclinico.domain.repository.NotificationPreferencesRepository
import com.example.medicaseclinico.domain.repository.TemperatureRepository
import com.example.medicaseclinico.domain.usecase.ShouldSaveCaseLocationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Contenedor de dependencias manual: instancias únicas para toda la app.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val temperatureRepository: TemperatureRepository by lazy {
        TemperatureRepositoryImpl(database.temperatureDao())
    }

    val alertEventRepository: AlertEventRepository by lazy {
        AlertEventRepositoryImpl(dao = database.alertEventDao())
    }

    val caseLocationRepository: CaseLocationRepository by lazy {
        CaseLocationRepositoryImpl(dao = database.caseLocationDao())
    }

    val geofenceRepository: GeofenceRepository by lazy {
        GeofenceRepositoryImpl(dao = database.geofenceDao())
    }

    val medicalReportExporter: MedicalReportExporter by lazy {
        PdfMedicalReportExporter(context = appContext)
    }

    val caseDeviceRepository: CaseDeviceRepository by lazy {
        AndroidCaseDeviceRepository(appContext, applicationScope)
    }

    val notificationPreferencesRepository: NotificationPreferencesRepository by lazy {
        DataStoreNotificationPreferencesRepository(appContext)
    }

    private val notificationPreferences: StateFlow<NotificationPreferences> by lazy {
        notificationPreferencesRepository.preferences.stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = NotificationPreferences()
        )
    }

    /** Cada alerta se guarda en el historial y luego se notifica. */
    val alertPublisher: AlertPublisher by lazy {
        RecordingAlertPublisher(
            delegate = SystemAlertPublisher(
                context = appContext,
                preferencesProvider = { notificationPreferences.value }
            ),
            alertEventRepository = alertEventRepository,
            scope = applicationScope
        )
    }

    val telemetrySynchronizer: CaseTelemetrySynchronizer by lazy {
        CaseTelemetrySynchronizer(
            deviceRepository = caseDeviceRepository,
            temperatureRepository = temperatureRepository,
            caseLocationRepository = caseLocationRepository,
            alertPublisher = alertPublisher,
            shouldSaveLocation = ShouldSaveCaseLocationUseCase(),
            scope = applicationScope
        )
    }
}