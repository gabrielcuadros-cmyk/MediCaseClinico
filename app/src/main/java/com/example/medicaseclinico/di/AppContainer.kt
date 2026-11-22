package com.example.medicaseclinico.di

import android.content.Context
import com.example.medicaseclinico.data.auth.FirebaseAuthRepository
import com.example.medicaseclinico.data.ble.AndroidCaseDeviceRepository
import com.example.medicaseclinico.data.local.AppDatabase
import com.example.medicaseclinico.data.mqtt.DataStoreMqttClientIdStore
import com.example.medicaseclinico.data.mqtt.MqttCaseDeviceRepository
import com.example.medicaseclinico.data.mqtt.MqttConfig
import com.example.medicaseclinico.data.notification.SystemAlertPublisher
import com.example.medicaseclinico.data.preferences.DataStoreAppearanceRepository
import com.example.medicaseclinico.data.preferences.DataStoreLossModeRepository
import com.example.medicaseclinico.data.preferences.DataStoreNotificationPreferencesRepository
import com.example.medicaseclinico.data.report.PdfMedicalReportExporter
import com.example.medicaseclinico.data.repository.AlertEventRepositoryImpl
import com.example.medicaseclinico.data.repository.CaseLocationRepositoryImpl
import com.example.medicaseclinico.data.repository.GeofenceRepositoryImpl
import com.example.medicaseclinico.data.repository.TemperatureRepositoryImpl
import com.example.medicaseclinico.data.sync.CaseTelemetrySynchronizer
import com.example.medicaseclinico.data.user.FirestoreAuthorizedUsersRepository
import com.example.medicaseclinico.data.user.FirestoreCaseAccessRepository
import com.example.medicaseclinico.data.user.FirestoreUserProfileRepository
import com.example.medicaseclinico.domain.alert.AlertPublisher
import com.example.medicaseclinico.domain.alert.RecordingAlertPublisher
import com.example.medicaseclinico.domain.model.CaseAccess
import com.example.medicaseclinico.domain.model.NotificationPreferences
import com.example.medicaseclinico.domain.model.restrictedTo
import com.example.medicaseclinico.domain.repository.AlertEventRepository
import com.example.medicaseclinico.domain.repository.AppearanceRepository
import com.example.medicaseclinico.domain.repository.AuthRepository
import com.example.medicaseclinico.domain.repository.AuthorizedUsersRepository
import com.example.medicaseclinico.domain.repository.CaseAccessRepository
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.repository.CaseLocationRepository
import com.example.medicaseclinico.domain.repository.GeofenceRepository
import com.example.medicaseclinico.domain.repository.LossModeRepository
import com.example.medicaseclinico.domain.repository.MedicalReportExporter
import com.example.medicaseclinico.domain.repository.NotificationPreferencesRepository
import com.example.medicaseclinico.domain.repository.TemperatureRepository
import com.example.medicaseclinico.domain.repository.UserProfileRepository
import com.example.medicaseclinico.domain.usecase.EvaluateGeofencesUseCase
import com.example.medicaseclinico.domain.usecase.ShouldSaveCaseLocationUseCase
import com.example.medicaseclinico.ui.widget.CaseWidgetStateSource
import com.example.medicaseclinico.ui.widget.MediCaseWidget
import com.example.medicaseclinico.ui.widget.WidgetRefresher
import com.example.medicaseclinico.ui.widget.alerts.RecentAlertsStateSource
import com.example.medicaseclinico.ui.widget.alerts.RecentAlertsWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(context = appContext)
    }

    val userProfileRepository: UserProfileRepository by lazy {
        FirestoreUserProfileRepository(context = appContext)
    }

    val caseAccessRepository: CaseAccessRepository by lazy {
        FirestoreCaseAccessRepository(context = appContext)
    }

    /**
     * Relación de la cuenta actual con el estuche (dueña, invitada o revocada),
     * para toda la app. null mientras se resuelve o sin sesión.
     *
     * Sin servicio de cuentas todos son dueños. Si las invitaciones no se pueden
     * leer (sin red y sin caché, o reglas de Firestore sin publicar) la cuenta
     * entra como dueña, igual que antes de existir las invitaciones: así nunca
     * queda nadie sin poder vigilar su insulina.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val caseAccess: StateFlow<CaseAccess?> by lazy {
        val access: Flow<CaseAccess?> = if (!authRepository.isAvailable) {
            flowOf(CaseAccess.Owner(uid = ""))
        } else {
            authRepository.currentUser.flatMapLatest { user ->
                if (user == null) {
                    flowOf(null)
                } else {
                    caseAccessRepository.observeAccess(user)
                        .catch<CaseAccess?> { error ->
                            if (error is CancellationException) throw error
                            emit(CaseAccess.Owner(user.uid))
                        }
                }
            }
        }
        access.stateIn(scope = applicationScope, started = SharingStarted.Eagerly, initialValue = null)
    }

    /** El acceso ya resuelto, para las pantallas: solo existen con sesión y acceso. */
    val resolvedCaseAccess: Flow<CaseAccess> by lazy { caseAccess.filterNotNull() }

    val authorizedUsersRepository: AuthorizedUsersRepository by lazy {
        FirestoreAuthorizedUsersRepository(context = appContext)
    }

    val medicalReportExporter: MedicalReportExporter by lazy {
        PdfMedicalReportExporter(context = appContext)
    }

    /**
     * MQTT si la app se compiló con un broker (local.properties); si no, Bluetooth de
     * respaldo. El resto de la app no sabe cuál de los dos usa.
     */
    val caseDeviceRepository: CaseDeviceRepository by lazy {
        val mqttConfig = MqttConfig.fromBuildConfig()
        if (mqttConfig != null) {
            MqttCaseDeviceRepository(
                config = mqttConfig,
                clientIdStore = DataStoreMqttClientIdStore(context = appContext),
                scope = applicationScope
            )
        } else {
            AndroidCaseDeviceRepository(context = appContext, scope = applicationScope)
        }
    }

    val notificationPreferencesRepository: NotificationPreferencesRepository by lazy {
        DataStoreNotificationPreferencesRepository(appContext)
    }

    val lossModeRepository: LossModeRepository by lazy {
        DataStoreLossModeRepository(context = appContext)
    }

    val appearanceRepository: AppearanceRepository by lazy {
        DataStoreAppearanceRepository(context = appContext)
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
                transport = caseDeviceRepository.transport,
                // Un invitado solo recibe lo que sus permisos le dejan ver.
                preferencesProvider = { notificationPreferences.value.restrictedTo(caseAccess.value) }
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
            geofenceRepository = geofenceRepository,
            alertPublisher = alertPublisher,
            shouldSaveLocation = ShouldSaveCaseLocationUseCase(),
            evaluateGeofences = EvaluateGeofencesUseCase(),
            scope = applicationScope
        )
    }

    /** Redibuja el widget "Estado de la insulina" con cada dato visible nuevo. */
    val caseWidgetRefresher: WidgetRefresher by lazy {
        WidgetRefresher(
            context = appContext,
            widget = MediCaseWidget(),
            changes = CaseWidgetStateSource.from(container = this).visibleChanges(),
            scope = applicationScope
        )
    }

    /** Redibuja el widget "Últimas alertas" cada vez que cambia el historial de alertas. */
    val recentAlertsWidgetRefresher: WidgetRefresher by lazy {
        WidgetRefresher(
            context = appContext,
            widget = RecentAlertsWidget(),
            changes = RecentAlertsStateSource.from(container = this).summaries(),
            scope = applicationScope
        )
    }
}