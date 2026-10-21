package com.example.medicaseclinico.testing

import com.example.medicaseclinico.domain.model.AccessEvent
import com.example.medicaseclinico.domain.model.AlertEvent
import com.example.medicaseclinico.domain.model.AuthUser
import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.CaseCommandError
import com.example.medicaseclinico.domain.model.CaseCommandException
import com.example.medicaseclinico.domain.model.CaseControlState
import com.example.medicaseclinico.domain.model.CaseLocation
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.CaseTelemetry
import com.example.medicaseclinico.domain.model.CaseTransport
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.DeviceIssue
import com.example.medicaseclinico.domain.model.Geofence
import com.example.medicaseclinico.domain.model.LossModeState
import com.example.medicaseclinico.domain.model.MedicalReport
import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.repository.AlertEventRepository
import com.example.medicaseclinico.domain.repository.AuthRepository
import com.example.medicaseclinico.domain.repository.AuthorizedUsersRepository
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.repository.CaseLocationRepository
import com.example.medicaseclinico.domain.repository.GeofenceRepository
import com.example.medicaseclinico.domain.repository.LossModeRepository
import com.example.medicaseclinico.domain.repository.MedicalReportExporter
import com.example.medicaseclinico.domain.repository.TemperatureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/*
 * Repositorios falsos para los tests de ViewModels. Guardan todo en memoria y
 * dejan provocar los fallos que en el teléfono dependen de la base de datos,
 * la nube o el Bluetooth. Ninguno contiene lógica de la app: solo devuelven lo
 * que el test les pone.
 */

/** Un `Flow` que falla al suscribirse, como una base de datos o una nube que no responde. */
private fun <T> failingFlow(error: Throwable): Flow<T> = flow { throw error }

class FakeAuthRepository(user: AuthUser? = null) : AuthRepository {
    override val currentUser = MutableStateFlow(user)

    override suspend fun signIn(email: String, password: String) = notUsed<AuthUser>()
    override suspend fun register(name: String, email: String, password: String) = notUsed<AuthUser>()
    override suspend fun sendPasswordReset(email: String) = notUsed<Unit>()
    override suspend fun signOut() = notUsed<Unit>()

    private fun <T> notUsed(): Result<T> = Result.failure(UnsupportedOperationException())
}

/**
 * @param echoWrites si las escrituras vuelven por el `Flow`, como hace
 * Firestore. En false simula un servidor que aún no ha respondido.
 */
class FakeAuthorizedUsersRepository(
    initial: List<AuthorizedUser> = emptyList(),
    private val echoWrites: Boolean = true
) : AuthorizedUsersRepository {
    val users = MutableStateFlow(initial)
    val saved = mutableListOf<AuthorizedUser>()
    val deleted = mutableListOf<String>()
    var failWrites = false
    var failReads = false

    override fun observeUsers(ownerId: String): Flow<List<AuthorizedUser>> =
        if (failReads) failingFlow(IllegalStateException("sin permiso")) else users

    override suspend fun saveUser(ownerId: String, user: AuthorizedUser): Result<String> {
        if (failWrites) return Result.failure(IllegalStateException("sin red"))
        val stored = if (user.id.isEmpty()) user.copy(id = "nuevo-${saved.size + 1}") else user
        saved += stored
        if (echoWrites) {
            users.update { current -> current.filterNot { it.id == stored.id } + stored }
        }
        return Result.success(stored.id)
    }

    override suspend fun deleteUser(ownerId: String, userId: String): Result<Unit> {
        if (failWrites) return Result.failure(IllegalStateException("sin red"))
        deleted += userId
        if (echoWrites) users.update { current -> current.filterNot { it.id == userId } }
        return Result.success(Unit)
    }
}

/**
 * Estuche falso. Las órdenes se anotan en [commands] (nunca el PIN) y
 * responden con [commandError] si se le pone uno.
 */
class FakeCaseDeviceRepository : CaseDeviceRepository {
    override var transport = CaseTransport.BLUETOOTH
    override val connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val deviceIssue = MutableStateFlow<DeviceIssue?>(null)
    override val telemetry: Flow<CaseTelemetry> = emptyFlow()
    override val caseLocation = MutableStateFlow<CaseLocationState>(CaseLocationState.Unavailable)
    override val controlState = MutableStateFlow<CaseControlState>(CaseControlState.Unavailable)
    override val accessEvents = MutableSharedFlow<AccessEvent>(extraBufferCapacity = 8)

    val commands = mutableListOf<String>()
    var commandError: CaseCommandError? = null
    var monitoringStarts = 0

    private fun run(command: String, onSuccess: () -> Unit = {}): Result<Unit> {
        commands += command
        val error = commandError ?: return Result.success(Unit).also { onSuccess() }
        return Result.failure(CaseCommandException(error))
    }

    override suspend fun setPinRequired(required: Boolean, currentPin: String): Result<Unit> =
        run("pinRequired=$required") {
            // Como el estuche real: confirma el cambio por su característica de estado.
            controlState.update { state ->
                if (state is CaseControlState.Ready) state.copy(pinRequired = required) else state
            }
        }

    override suspend fun changePin(currentPin: String, newPin: String): Result<Unit> = run("changePin")

    override suspend fun setAlarm(on: Boolean): Result<Unit> = run("alarm=$on")

    override suspend fun setGpsIntervalSeconds(seconds: Int): Result<Unit> = run("gps=$seconds")

    override fun startMonitoring() {
        monitoringStarts++
    }

    override fun stopMonitoring() = Unit
}

class FakeCaseLocationRepository(lastKnown: CaseLocation? = null) : CaseLocationRepository {
    val lastKnown = MutableStateFlow(lastKnown)
    var readError: Throwable? = null

    override fun observeLastKnownLocation(): Flow<CaseLocation?> =
        readError?.let { failingFlow(it) } ?: lastKnown

    override suspend fun saveLocation(location: CaseLocation): Result<Long> {
        lastKnown.value = location
        return Result.success(1L)
    }

    override suspend fun deleteOlderThan(timestamp: Long): Result<Int> = Result.success(0)
}

class FakeGeofenceRepository(initial: List<Geofence> = emptyList()) : GeofenceRepository {
    val geofences = MutableStateFlow(initial)

    override fun observeGeofences(): Flow<List<Geofence>> = geofences

    override suspend fun saveGeofence(geofence: Geofence): Result<Long> {
        geofences.update { it + geofence }
        return Result.success(geofence.id)
    }

    override suspend fun deleteGeofence(id: Long): Result<Unit> {
        geofences.update { current -> current.filterNot { it.id == id } }
        return Result.success(Unit)
    }
}

class FakeAlertEventRepository(initial: List<AlertEvent> = emptyList()) : AlertEventRepository {
    val events = MutableStateFlow(initial)

    override fun observeAlertEvents(): Flow<List<AlertEvent>> = events

    override fun observeUnreadCount(): Flow<Int> = events.map { list -> list.count { !it.isRead } }

    override suspend fun recordAlert(event: AlertEvent): Result<Long> {
        events.update { it + event }
        return Result.success(event.id)
    }

    override suspend fun markAllAsRead(): Result<Unit> {
        events.update { list -> list.map { it.copy(isRead = true) } }
        return Result.success(Unit)
    }
}

class FakeLossModeRepository(initial: LossModeState = LossModeState()) : LossModeRepository {
    val stored = MutableStateFlow(initial)
    var failWrites = false
    var failReads = false

    override val state: Flow<LossModeState>
        get() = if (failReads) failingFlow(IllegalStateException("archivo ilegible")) else stored

    override suspend fun update(transform: (LossModeState) -> LossModeState): Result<Unit> {
        if (failWrites) return Result.failure(IllegalStateException("disco lleno"))
        stored.update(transform)
        return Result.success(Unit)
    }
}

/** Historial térmico falso. Anota qué ventana y qué resumen pidió la pantalla. */
class FakeTemperatureRepository(
    latest: TemperatureReading? = null,
    batteryLevel: Int? = null,
    buckets: List<TemperatureBucket> = emptyList()
) : TemperatureRepository {
    val latest = MutableStateFlow(latest)
    val batteryLevel = MutableStateFlow(batteryLevel)
    val buckets = MutableStateFlow(buckets)
    val bucketRequests = mutableListOf<Pair<Long, Long>>()
    var readError: Throwable? = null

    override fun observeLatestReading(): Flow<TemperatureReading?> =
        readError?.let { failingFlow(it) } ?: latest

    override fun observeReadingsSince(since: Long): Flow<List<TemperatureReading>> = emptyFlow()

    override fun observeBucketsSince(since: Long, bucketMs: Long): Flow<List<TemperatureBucket>> {
        bucketRequests += since to bucketMs
        return buckets
    }

    override fun observeAlertReadings(): Flow<List<TemperatureReading>> = emptyFlow()

    override fun observeLatestBatteryLevel(): Flow<Int?> = batteryLevel

    override suspend fun saveReading(reading: TemperatureReading): Result<Long> = Result.success(1L)

    override suspend fun saveReadings(readings: List<TemperatureReading>): Result<Unit> =
        Result.success(Unit)

    override suspend fun getReadingsBetween(from: Long, to: Long): Result<List<TemperatureReading>> =
        Result.success(emptyList())

    override suspend fun purgeReadingsOlderThan(olderThan: Long): Result<Int> = Result.success(0)
}

class FakeMedicalReportExporter : MedicalReportExporter {
    val exported = mutableListOf<MedicalReport>()
    var fail = false

    override suspend fun export(report: MedicalReport): Result<String> {
        if (fail) return Result.failure(IllegalStateException("sin espacio"))
        exported += report
        return Result.success("content://reportes/${exported.size}.pdf")
    }
}
