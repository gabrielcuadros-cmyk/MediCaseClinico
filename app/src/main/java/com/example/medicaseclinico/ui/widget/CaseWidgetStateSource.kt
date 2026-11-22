package com.example.medicaseclinico.ui.widget

import com.example.medicaseclinico.di.AppContainer
import com.example.medicaseclinico.domain.model.CaseAccess
import com.example.medicaseclinico.domain.model.CaseCapability
import com.example.medicaseclinico.domain.model.CaseWidgetSummary
import com.example.medicaseclinico.domain.repository.AlertEventRepository
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.example.medicaseclinico.domain.repository.TemperatureRepository
import com.example.medicaseclinico.domain.time.DateTimeLabels
import com.example.medicaseclinico.domain.usecase.BuildCaseWidgetSummaryUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Estado que dibuja el widget de la pantalla de inicio.
 *
 * Mismo patrón que las pantallas (Loading / Error / datos), pero sin
 * ViewModel: el widget vive en el launcher y Glance recolecta el flujo
 * mientras dura su sesión. El estado vacío es [CaseWidgetSummary.NoReadings].
 */
sealed interface CaseWidgetUiState {
    data object Loading : CaseWidgetUiState

    /** No se pudo leer el historial: el widget ofrece "Reintentar". */
    data object Error : CaseWidgetUiState

    /** Sin sesión en la app: no se enseña ningún dato del estuche. */
    data object SignedOut : CaseWidgetUiState

    /** La cuenta es invitada sin permiso de temperatura, o su acceso fue revocado. */
    data object NoPermission : CaseWidgetUiState

    /**
     * @param readingTime hora de la última lectura ya formateada
     *   ("20 nov, 14:32"), o null si no hay lecturas.
     */
    data class Ready(
        val summary: CaseWidgetSummary,
        val readingTime: String?
    ) : CaseWidgetUiState
}

/**
 * Une los datos reales que muestra el widget: última lectura (Room), enlace
 * con el estuche y alertas sin ver. Es la única puerta del widget a los
 * repositorios, para que la lógica no quede en los Composables de Glance.
 */
class CaseWidgetStateSource(
    private val temperatureRepository: TemperatureRepository,
    private val deviceRepository: CaseDeviceRepository,
    private val alertEventRepository: AlertEventRepository,
    private val caseAccess: Flow<CaseAccess?> = flowOf(CaseAccess.Owner(uid = "")),
    private val buildSummary: BuildCaseWidgetSummaryUseCase = BuildCaseWidgetSummaryUseCase(),
    private val dateTimeLabels: DateTimeLabels = DateTimeLabels.system(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    /** Resumen del estuche cada vez que cambia alguno de los tres datos. */
    fun summaries(): Flow<CaseWidgetSummary> = combine(
        temperatureRepository.observeLatestReading(),
        deviceRepository.connectionStatus,
        alertEventRepository.observeUnreadCount()
    ) { latest, connection, unread ->
        buildSummary(
            latestReading = latest,
            connection = connection,
            unreadAlerts = unread,
            now = clock()
        )
    }

    /**
     * Lo que se ve en el widget, para decidir cuándo redibujarlo: la hora de
     * la lectura solo cuenta al minuto, que es como se muestra.
     */
    fun visibleChanges(): Flow<Any> = combine(
        summaries().map { summary ->
            when (summary) {
                is CaseWidgetSummary.NoReadings -> summary
                is CaseWidgetSummary.Reading -> summary.copy(timestamp = summary.timestamp / MINUTE_MS)
            }
        },
        // Cambiar de cuenta o de permisos también cambia lo que se ve.
        caseAccess.map { access -> access?.can(CaseCapability.VIEW_TEMPERATURE) }
    ) { summary, canView -> summary to canView }

    /**
     * Estado del widget. Un flujo nuevo en cada llamada: "Reintentar" vuelve a
     * empezar la lectura en lugar de reutilizar uno que ya terminó en error.
     */
    fun uiState(): Flow<CaseWidgetUiState> = combine(summaries(), caseAccess) { summary, access ->
        when {
            access == null -> CaseWidgetUiState.SignedOut
            !access.can(CaseCapability.VIEW_TEMPERATURE) -> CaseWidgetUiState.NoPermission
            else -> CaseWidgetUiState.Ready(
                summary = summary,
                readingTime = (summary as? CaseWidgetSummary.Reading)
                    ?.let { dateTimeLabels.readingTime(it.timestamp) }
            )
        }
    }
        .catch<CaseWidgetUiState> { error ->
            if (error is CancellationException) throw error
            emit(CaseWidgetUiState.Error)
        }

    companion object {
        private const val MINUTE_MS = 60_000L

        /** Con las instancias únicas de la app. */
        fun from(container: AppContainer): CaseWidgetStateSource = CaseWidgetStateSource(
            temperatureRepository = container.temperatureRepository,
            deviceRepository = container.caseDeviceRepository,
            alertEventRepository = container.alertEventRepository,
            caseAccess = container.caseAccess
        )
    }
}
