package com.example.medicaseclinico.ui.screens.history

import com.example.medicaseclinico.domain.model.HistoryRange
import com.example.medicaseclinico.domain.model.TemperatureBucket
import com.example.medicaseclinico.domain.model.TemperatureReading
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.usecase.CalculateTemperatureStatsUseCase
import com.example.medicaseclinico.domain.usecase.DetectTemperatureEventsUseCase
import com.example.medicaseclinico.testing.FakeMedicalReportExporter
import com.example.medicaseclinico.testing.FakeTemperatureRepository
import com.example.medicaseclinico.testing.MainDispatcherRule
import com.example.medicaseclinico.testing.keepObserved
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemperatureHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Lejos de cero para que "hace 30 días" no dé una fecha negativa.
    private val now = 100 * DAY_MS

    private val temperature = FakeTemperatureRepository()
    private val exporter = FakeMedicalReportExporter()

    private val latest = TemperatureReading(
        temperatureCelsius = 4.2f,
        timestamp = now - 60_000L,
        isAlertTriggered = false
    )

    private fun bucket(minutesAgo: Int, celsius: Float = 4.0f) = TemperatureBucket(
        startTimestamp = now - minutesAgo * 60_000L,
        averageCelsius = celsius,
        minCelsius = celsius,
        maxCelsius = celsius,
        sampleCount = 12,
        outOfRangeCount = 0
    )

    private fun TestScope.viewModel(): TemperatureHistoryViewModel {
        val viewModel = TemperatureHistoryViewModel(
            temperatureRepository = temperature,
            reportExporter = exporter,
            calculateStats = CalculateTemperatureStatsUseCase(),
            detectEvents = DetectTemperatureEventsUseCase(),
            clock = { now },
            computationDispatcher = UnconfinedTestDispatcher(testScheduler),
            refreshIntervalMs = REFRESH_MS
        )
        keepObserved(viewModel.uiState)
        return viewModel
    }

    private fun TemperatureHistoryViewModel.success() =
        uiState.value as TemperatureHistoryUiState.Success

    @Test
    fun `con lecturas en el rango muestra la grafica y la temperatura actual`() = runTest {
        temperature.latest.value = latest
        temperature.buckets.value = listOf(bucket(minutesAgo = 20), bucket(minutesAgo = 10))

        val state = viewModel().success()

        assertEquals(HistoryRange.LAST_24_HOURS, state.selectedRange)
        assertEquals(2, state.buckets.size)
        assertEquals(4.2f, state.current.celsius)
        assertEquals(TemperatureStatus.SAFE, state.current.status)
        assertEquals(now - DAY_MS, state.rangeStart)
        assertEquals(now, state.rangeEnd)
    }

    @Test
    fun `pide a la base de datos la ventana y el resumen del rango elegido`() = runTest {
        temperature.latest.value = latest
        val viewModel = viewModel()

        viewModel.onRangeSelected(HistoryRange.LAST_7_DAYS)

        assertEquals(
            listOf(
                now - HistoryRange.LAST_24_HOURS.durationMs to HistoryRange.LAST_24_HOURS.bucketMs,
                now - HistoryRange.LAST_7_DAYS.durationMs to HistoryRange.LAST_7_DAYS.bucketMs
            ),
            temperature.bucketRequests
        )
        assertEquals(HistoryRange.LAST_7_DAYS, viewModel.uiState.value.selectedRange)
    }

    @Test
    fun `un rango sin lecturas esta vacio pero conserva la ultima temperatura conocida`() = runTest {
        temperature.latest.value = latest

        val state = viewModel().uiState.value as TemperatureHistoryUiState.Empty

        assertEquals(4.2f, state.current?.celsius)
    }

    @Test
    fun `si el estuche nunca reporto no se muestra ninguna temperatura`() = runTest {
        val state = viewModel().uiState.value as TemperatureHistoryUiState.Empty

        assertNull(state.current)
    }

    @Test
    fun `la temperatura actual se actualiza con cada lectura aunque la grafica espere`() = runTest {
        temperature.latest.value = latest
        temperature.buckets.value = listOf(bucket(minutesAgo = 10))
        val viewModel = viewModel()

        temperature.latest.value = latest.copy(temperatureCelsius = 9.1f, timestamp = now)
        temperature.buckets.value = listOf(bucket(minutesAgo = 10), bucket(minutesAgo = 0, celsius = 9.1f))

        // El dato clínico llega al instante; la gráfica todavía es la anterior.
        assertEquals(9.1f, viewModel.success().current.celsius)
        assertEquals(TemperatureStatus.CRITICAL, viewModel.success().current.status)
        assertEquals(1, viewModel.success().buckets.size)

        advanceTimeBy(REFRESH_MS + 1)

        assertEquals(2, viewModel.success().buckets.size)
    }

    @Test
    fun `un fallo al leer se muestra como error del mismo rango y se puede reintentar`() = runTest {
        temperature.readError = IllegalStateException("base de datos dañada")
        val viewModel = viewModel()
        assertEquals(
            TemperatureHistoryUiState.Error(HistoryRange.LAST_24_HOURS),
            viewModel.uiState.value
        )

        temperature.readError = null
        temperature.latest.value = latest
        temperature.buckets.value = listOf(bucket(minutesAgo = 10))
        viewModel.retry()

        assertEquals(1, viewModel.success().buckets.size)
    }

    @Test
    fun `exportar genera el reporte con lo que hay en pantalla`() = runTest {
        temperature.latest.value = latest
        temperature.buckets.value = listOf(bucket(minutesAgo = 10))
        val viewModel = viewModel()

        viewModel.exportReport()

        val report = exporter.exported.single()
        assertEquals(HistoryRange.LAST_24_HOURS, report.range)
        assertEquals(4.2f, report.currentCelsius)
        assertEquals(viewModel.success().buckets, report.buckets)
        assertEquals(now, report.generatedAt)
        assertEquals(ReportExportState.Ready("content://reportes/1.pdf"), viewModel.exportState.value)

        viewModel.onExportHandled()
        assertEquals(ReportExportState.Idle, viewModel.exportState.value)
    }

    @Test
    fun `sin lecturas no se genera ningun reporte`() = runTest {
        val viewModel = viewModel()

        viewModel.exportReport()

        assertTrue(exporter.exported.isEmpty())
        assertEquals(ReportExportState.Idle, viewModel.exportState.value)
    }

    @Test
    fun `los fallos al generar y al compartir se distinguen`() = runTest {
        temperature.latest.value = latest
        temperature.buckets.value = listOf(bucket(minutesAgo = 10))
        val viewModel = viewModel()

        exporter.fail = true
        viewModel.exportReport()
        assertEquals(
            ReportExportState.Failed(ReportExportError.GENERATION),
            viewModel.exportState.value
        )

        viewModel.onShareFailed()
        assertEquals(
            ReportExportState.Failed(ReportExportError.NO_SHARE_APP),
            viewModel.exportState.value
        )
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60_000L
        const val REFRESH_MS = 30_000L
    }
}
