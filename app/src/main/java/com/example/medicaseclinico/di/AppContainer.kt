package com.example.medicaseclinico.di

import android.content.Context
import com.example.medicaseclinico.data.ble.AndroidCaseDeviceRepository
import com.example.medicaseclinico.data.local.AppDatabase
import com.example.medicaseclinico.data.notification.SystemAlertPublisher
import com.example.medicaseclinico.data.preferences.DataStoreNotificationPreferencesRepository
import com.example.medicaseclinico.data.report.PdfMedicalReportExporter
import com.example.medicaseclinico.data.repository.TemperatureRepositoryImpl
import com.example.medicaseclinico.data.sync.CaseTelemetrySynchronizer
import com.example.medicaseclinico.domain.alert.AlertPublisher
import com.example.medicaseclinico.domain.model.NotificationPreferences
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.repository.MedicalReportExporter
import com.example.medicaseclinico.domain.repository.NotificationPreferencesRepository
import com.example.medicaseclinico.domain.repository.TemperatureRepository
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

    val alertPublisher: AlertPublisher by lazy {
        SystemAlertPublisher(
            context = appContext,
            preferencesProvider = { notificationPreferences.value }
        )
    }

    val telemetrySynchronizer: CaseTelemetrySynchronizer by lazy {
        CaseTelemetrySynchronizer(
            deviceRepository = caseDeviceRepository,
            temperatureRepository = temperatureRepository,
            alertPublisher = alertPublisher,
            scope = applicationScope
        )
    }
}