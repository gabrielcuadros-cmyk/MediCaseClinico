package com.example.medicaseclinico.data.sync

import com.example.medicaseclinico.domain.alert.AlertPublisher
import com.example.medicaseclinico.domain.model.AccessEvent
import com.example.medicaseclinico.domain.model.AccessEventType
import com.example.medicaseclinico.domain.model.CaseControlState
import com.example.medicaseclinico.domain.model.CaseGpsReading
import com.example.medicaseclinico.domain.model.CaseLocation
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.CaseTelemetry
import com.example.medicaseclinico.domain.model.CaseTransport
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.DeviceIssue
import com.example.medicaseclinico.domain.model.Geofence
import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.repository.CaseLocationRepository
import com.example.medicaseclinico.domain.repository.GeofenceRepository
import com.example.medicaseclinico.domain.repository.TemperatureRepository
import com.example.medicaseclinico.domain.usecase.EvaluateGeofencesUseCase
import com.example.medicaseclinico.domain.usecase.ShouldSaveCaseLocationUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CaseTelemetrySynchronizerGeofenceTest {

    private class FakeCaseDeviceRepository : CaseDeviceRepository {
        override val transport = CaseTransport.BLUETOOTH
        override val connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTED)
        override val deviceIssue = MutableStateFlow<DeviceIssue?>(null)
        override val telemetry: Flow<CaseTelemetry> = emptyFlow()
        override val caseLocation = MutableStateFlow<CaseLocationState>(CaseLocationState.Unavailable)
        override val controlState = MutableStateFlow<CaseControlState>(CaseControlState.Unavailable)
        override val accessEvents = MutableSharedFlow<AccessEvent>(extraBufferCapacity = 8)
        override suspend fun setPinRequired(required: Boolean, currentPin: String) = Result.success(Unit)
        override suspend fun changePin(currentPin: String, newPin: String) = Result.success(Unit)
        override suspend fun setAlarm(on: Boolean) = Result.success(Unit)
        override suspend fun setGpsIntervalSeconds(seconds: Int) = Result.success(Unit)
        override fun startMonitoring() = Unit
        override fun stopMonitoring() = Unit
    }

    private class FakeTemperatureRepository : TemperatureRepository {
        override fun observeLatestReading(): Flow<TemperatureReading?> = flowOf(null)
        override fun observeReadingsSince(since: Long): Flow<List<TemperatureReading>> = flowOf(emptyList())
        override fun observeBucketsSince(since: Long, bucketMs: Long): Flow<List<TemperatureBucket>> =
            flowOf(emptyList())
        override fun observeAlertReadings(): Flow<List<TemperatureReading>> = flowOf(emptyList())
        override fun observeLatestBatteryLevel(): Flow<Int?> = flowOf(null)
        override suspend fun saveReading(reading: TemperatureReading): Result<Long> = Result.success(1L)
        override suspend fun saveReadings(readings: List<TemperatureReading>): Result<Unit> =
            Result.success(Unit)
        override suspend fun getReadingsBetween(from: Long, to: Long): Result<List<TemperatureReading>> =
            Result.success(emptyList())
        override suspend fun purgeReadingsOlderThan(olderThan: Long): Result<Int> = Result.success(0)
    }

    private class FakeCaseLocationRepository : CaseLocationRepository {
        val saved = mutableListOf<CaseLocation>()

        override fun observeLastKnownLocation(): Flow<CaseLocation?> = flowOf(saved.lastOrNull())
        override suspend fun deleteOlderThan(timestamp: Long): Result<Int> = Result.success(0)

        override suspend fun saveLocation(location: CaseLocation): Result<Long> {
            saved += location
            return Result.success(saved.size.toLong())
        }
    }

    private class FakeGeofenceRepository(
        initial: List<Geofence>,
        private val failing: Boolean
    ) : GeofenceRepository {
        val geofences = MutableStateFlow(initial)

        override fun observeGeofences(): Flow<List<Geofence>> =
            if (failing) flow { throw IOException("base dañada") } else geofences

        override suspend fun saveGeofence(geofence: Geofence): Result<Long> = Result.success(geofence.id)
        override suspend fun deleteGeofence(id: Long): Result<Unit> = Result.success(Unit)
    }

    private class FakeAlertPublisher : AlertPublisher {
        val geofenceCalls = mutableListOf<String>()
        val accessCalls = mutableListOf<String>()

        override fun onAccessDenied(failedAttempts: Int, lockedOut: Boolean) {
            accessCalls += if (lockedOut) "locked out" else "denied $failedAttempts"
        }
        override fun onAccessGranted() {
            accessCalls += "granted"
        }

        override fun onTemperature(celsius: Float, status: TemperatureStatus) = Unit
        override fun onBatteryLevel(percent: Int) = Unit
        override fun onConnectionLost() = Unit
        override fun onConnectionRestored() = Unit

        override fun onGeofenceExited(geofence: Geofence) {
            geofenceCalls += "exited ${geofence.name}"
        }
        override fun onGeofenceEntered(geofence: Geofence) {
            geofenceCalls += "entered ${geofence.name}"
        }
    }

    private class Harness(
        private val scope: TestScope,
        initialGeofences: List<Geofence>,
        failingGeofences: Boolean
    ) {
        private var now = 1_000_000L
        val device = FakeCaseDeviceRepository()
        val locations = FakeCaseLocationRepository()
        val geofences = FakeGeofenceRepository(initialGeofences, failingGeofences)
        val alerts = FakeAlertPublisher()
        val synchronizer = CaseTelemetrySynchronizer(
            deviceRepository = device,
            temperatureRepository = FakeTemperatureRepository(),
            caseLocationRepository = locations,
            geofenceRepository = geofences,
            alertPublisher = alerts,
            shouldSaveLocation = ShouldSaveCaseLocationUseCase(),
            evaluateGeofences = EvaluateGeofencesUseCase(),
            scope = scope.backgroundScope,
            clock = { now }
        )

        /** El estuche informa su posición, a [meters] al norte del centro de Casa, un segundo después. */
        fun caseAt(meters: Double) = report(
            CaseLocationState.Reported(
                reading = CaseGpsReading.Fix(latitude = metersNorth(meters), longitude = LONGITUDE),
                receivedAt = now
            )
        )

        fun report(state: CaseLocationState) {
            now += 1_000L
            device.caseLocation.value = state
            scope.runCurrent()
        }

        fun setGeofences(list: List<Geofence>) {
            geofences.geofences.value = list
            scope.runCurrent()
        }

        /** El estuche avisa de un intento en su teclado. */
        fun keypad(type: AccessEventType, failedAttempts: Int) {
            device.accessEvents.tryEmit(
                AccessEvent(type = type, failedAttempts = failedAttempts, receivedAt = now)
            )
            scope.runCurrent()
        }
    }

    private fun syncTest(
        geofences: List<Geofence> = listOf(home),
        failingGeofences: Boolean = false,
        body: Harness.() -> Unit
    ) = runTest {
        val harness = Harness(this, geofences, failingGeofences)
        harness.synchronizer.start()
        runCurrent()
        harness.body()
    }

    @Test
    fun `salir de una zona avisa una sola vez`() = syncTest {
        caseAt(0.0)
        caseAt(400.0)
        caseAt(450.0)

        assertEquals(listOf("exited Casa"), alerts.geofenceCalls)
    }

    @Test
    fun `volver a la zona avisa la entrada`() = syncTest {
        caseAt(0.0)
        caseAt(400.0)
        caseAt(50.0)

        assertEquals(listOf("exited Casa", "entered Casa"), alerts.geofenceCalls)
    }

    @Test
    fun `el vaiven del gps en el borde no avisa`() = syncTest {
        listOf(145.0, 155.0, 140.0, 165.0, 150.0).forEach { caseAt(it) }

        assertTrue(alerts.geofenceCalls.isEmpty())
    }

    @Test
    fun `la salida se avisa aunque esa posicion no se guarde`() = syncTest {
        caseAt(0.0)
        // Un segundo después: el muestreo aún no deja guardar otra posición.
        caseAt(400.0)

        assertEquals(1, locations.saved.size)
        assertEquals(listOf("exited Casa"), alerts.geofenceCalls)
    }

    @Test
    fun `si el estuche reaparece fuera tras perder la senal o el enlace se avisa la salida`() = syncTest {
        caseAt(0.0)
        report(CaseLocationState.Reported(reading = CaseGpsReading.NoSignal, receivedAt = 0L))
        report(CaseLocationState.Unavailable)
        assertTrue(alerts.geofenceCalls.isEmpty())

        caseAt(400.0)

        assertEquals(listOf("exited Casa"), alerts.geofenceCalls)
    }

    @Test
    fun `una zona creada con el estuche ya fuera no avisa`() = syncTest(geofences = emptyList()) {
        caseAt(400.0)
        setGeofences(listOf(home))
        caseAt(410.0)

        assertTrue(alerts.geofenceCalls.isEmpty())
    }

    @Test
    fun `una zona desactivada deja de avisar`() = syncTest {
        caseAt(0.0)
        setGeofences(listOf(home.copy(isActive = false)))
        caseAt(400.0)

        assertTrue(alerts.geofenceCalls.isEmpty())
    }

    @Test
    fun `si falla la lectura de zonas no hay avisos y la posicion se sigue guardando`() =
        syncTest(failingGeofences = true) {
            caseAt(0.0)
            caseAt(400.0)

            assertTrue(alerts.geofenceCalls.isEmpty())
            assertEquals(1, locations.saved.size)
        }

    @Test
    fun `los intentos en el teclado del estuche llegan como alertas de seguridad`() = syncTest {
        keypad(AccessEventType.DENIED, failedAttempts = 1)
        keypad(AccessEventType.DENIED, failedAttempts = 2)
        keypad(AccessEventType.LOCKED_OUT, failedAttempts = 3)
        keypad(AccessEventType.GRANTED, failedAttempts = 0)

        assertEquals(listOf("denied 1", "denied 2", "locked out", "granted"), alerts.accessCalls)
        assertTrue(alerts.geofenceCalls.isEmpty())
    }

    private companion object {
        // Plaza de Armas de Arequipa.
        const val LATITUDE = -16.3989
        const val LONGITUDE = -71.5369

        val home = Geofence(
            id = 1L,
            name = "Casa",
            latitude = LATITUDE,
            longitude = LONGITUDE,
            radiusMeters = 150.0
        )

        /** 0.0001 grados de latitud son unos 11 metros. */
        fun metersNorth(meters: Double) = LATITUDE + meters / 11.12 * 0.0001
    }
}
