package com.example.medicaseclinico.domain.model

/**
 * Lo que muestra el widget de la pantalla de inicio, ya decidido.
 *
 * El widget se dibuja fuera de la app (en el launcher) y no tiene ViewModel:
 * por eso recibe un resumen cerrado, calculado por
 * [com.example.medicaseclinico.domain.usecase.BuildCaseWidgetSummaryUseCase],
 * y solo lo pinta. Nunca lleva valores inventados: sin lecturas es [NoReadings].
 */
sealed interface CaseWidgetSummary {
    /** Estado del enlace con el estuche ahora mismo. */
    val connection: ConnectionStatus

    /** Alertas que el usuario aún no ha visto (insignia del acceso "Alertas"). */
    val unreadAlerts: Int

    /** El estuche nunca envió una temperatura: el widget muestra "--". */
    data class NoReadings(
        override val connection: ConnectionStatus,
        override val unreadAlerts: Int
    ) : CaseWidgetSummary

    /**
     * Última temperatura guardada en Room.
     *
     * @param timestamp momento de la lectura (epoch ms): el widget muestra la
     *   hora exacta y no "hace X min", porque solo se redibuja cuando cambia
     *   algo y un tiempo relativo quedaría desfasado.
     * @param isOutdated true si, sin enlace, la lectura es tan antigua que ya no
     *   describe la insulina: el widget no le pone la insignia de estado.
     */
    data class Reading(
        val celsius: Float,
        val status: TemperatureStatus,
        val timestamp: Long,
        val isOutdated: Boolean,
        override val connection: ConnectionStatus,
        override val unreadAlerts: Int
    ) : CaseWidgetSummary
}
