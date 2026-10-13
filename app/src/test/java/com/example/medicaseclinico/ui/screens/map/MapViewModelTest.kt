package com.example.medicaseclinico.ui.screens.map

import com.example.medicaseclinico.domain.model.CaseGpsReading
import com.example.medicaseclinico.domain.model.CaseLocation
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.Geofence
import com.example.medicaseclinico.domain.model.RelativeTime
import com.example.medicaseclinico.domain.usecase.LocateCaseInGeofencesUseCase
import com.example.medicaseclinico.testing.FakeCaseDeviceRepository
import com.example.medicaseclinico.testing.FakeCaseLocationRepository
import com.example.medicaseclinico.testing.FakeGeofenceRepository
import com.example.medicaseclinico.testing.MainDispatcherRule
import com.example.medicaseclinico.testing.keepObserved
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = 60 * MINUTE_MS

    private val device = FakeCaseDeviceRepository()
    private val locations = FakeCaseLocationRepository()
    private val geofences = FakeGeofenceRepository()

    private val saved = CaseLocation(
        latitude = PLAZA_LATITUDE,
        longitude = PLAZA_LONGITUDE,
        timestamp = now - 7 * MINUTE_MS
    )

    private fun liveFix(latitude: Double, longitude: Double) = CaseLocationState.Reported(
        reading = CaseGpsReading.Fix(latitude = latitude, longitude = longitude),
        receivedAt = now
    )

    private fun TestScope.viewModel(): MapViewModel {
        val viewModel = MapViewModel(
            caseDeviceRepository = device,
            caseLocationRepository = locations,
            geofenceRepository = geofences,
            locateCaseInGeofences = LocateCaseInGeofencesUseCase(),
            clock = { now }
        )
        keepObserved(viewModel.uiState)
        return viewModel
    }

    private fun MapViewModel.success() = uiState.value as MapUiState.Success

    @Test
    fun `sin ninguna posicion el mapa esta vacio y dice por que`() = runTest {
        val viewModel = viewModel()
        assertEquals(MapUiState.Empty(CaseLiveStatus.NO_LINK), viewModel.uiState.value)

        device.caseLocation.value = CaseLocationState.NotSupported
        assertEquals(MapUiState.Empty(CaseLiveStatus.GPS_NOT_SUPPORTED), viewModel.uiState.value)

        device.caseLocation.value =
            CaseLocationState.Reported(reading = CaseGpsReading.NoSignal, receivedAt = now)
        assertEquals(MapUiState.Empty(CaseLiveStatus.NO_GPS_SIGNAL), viewModel.uiState.value)
    }

    @Test
    fun `con posicion en vivo se muestra esa con su hora de recepcion`() = runTest {
        device.caseLocation.value = liveFix(PLAZA_LATITUDE, PLAZA_LONGITUDE)

        val state = viewModel().success()

        assertEquals(PLAZA_LATITUDE, state.latitude, 0.0)
        assertEquals(PLAZA_LONGITUDE, state.longitude, 0.0)
        assertEquals(now, state.updatedAt)
        assertEquals(RelativeTime.JustNow, state.elapsed)
        assertEquals(CaseLiveStatus.LIVE, state.liveStatus)
    }

    @Test
    fun `sin enlace se muestra la ultima ubicacion guardada con su hora`() = runTest {
        locations.lastKnown.value = saved

        val state = viewModel().success()

        assertEquals(saved.latitude, state.latitude, 0.0)
        assertEquals(saved.timestamp, state.updatedAt)
        assertEquals(RelativeTime.Minutes(7), state.elapsed)
        assertEquals(CaseLiveStatus.NO_LINK, state.liveStatus)
    }

    @Test
    fun `la posicion en vivo manda sobre la guardada y al perder la senal se vuelve a ella`() = runTest {
        locations.lastKnown.value = saved
        device.caseLocation.value = liveFix(FAR_LATITUDE, FAR_LONGITUDE)
        val viewModel = viewModel()

        assertEquals(FAR_LATITUDE, viewModel.success().latitude, 0.0)
        assertEquals(CaseLiveStatus.LIVE, viewModel.success().liveStatus)

        device.caseLocation.value =
            CaseLocationState.Reported(reading = CaseGpsReading.NoSignal, receivedAt = now)

        assertEquals(saved.latitude, viewModel.success().latitude, 0.0)
        assertEquals(CaseLiveStatus.NO_GPS_SIGNAL, viewModel.success().liveStatus)
    }

    @Test
    fun `las zonas se marcan dentro o fuera segun la posicion mostrada`() = runTest {
        geofences.geofences.value = listOf(
            Geofence(
                id = 1,
                name = "Clínica",
                latitude = FAR_LATITUDE,
                longitude = FAR_LONGITUDE,
                radiusMeters = 500.0
            ),
            Geofence(
                id = 2,
                name = "Casa",
                latitude = PLAZA_LATITUDE,
                longitude = PLAZA_LONGITUDE,
                radiusMeters = 150.0
            )
        )
        device.caseLocation.value = liveFix(PLAZA_LATITUDE, PLAZA_LONGITUDE)

        val state = viewModel().success()

        // La zona que contiene al estuche va primero: es la que resume la tarjeta.
        assertEquals("Casa", state.nearestGeofence?.name)
        assertTrue(state.geofences.first { it.name == "Casa" }.isInside)
        assertFalse(state.geofences.first { it.name == "Clínica" }.isInside)
    }

    @Test
    fun `sin zonas configuradas no hay ninguna que resumir`() = runTest {
        locations.lastKnown.value = saved

        assertNull(viewModel().success().nearestGeofence)
    }

    @Test
    fun `un fallo al leer las posiciones se muestra como error y se puede reintentar`() = runTest {
        locations.readError = IllegalStateException("base de datos dañada")
        val viewModel = viewModel()
        assertEquals(MapUiState.Error, viewModel.uiState.value)

        locations.readError = null
        locations.lastKnown.value = saved
        viewModel.retry()

        assertEquals(saved.latitude, viewModel.success().latitude, 0.0)
    }

    private companion object {
        const val MINUTE_MS = 60_000L

        // Plaza de Armas de Arequipa y un punto a varios kilómetros.
        const val PLAZA_LATITUDE = -16.3989
        const val PLAZA_LONGITUDE = -71.5369
        const val FAR_LATITUDE = -16.4500
        const val FAR_LONGITUDE = -71.5000
    }
}
