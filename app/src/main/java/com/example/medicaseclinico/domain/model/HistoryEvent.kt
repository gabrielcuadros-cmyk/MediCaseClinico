package com.example.medicaseclinico.domain.model

/**
 * Evento de la "Línea de eventos" del Historial Térmico.
 *
 * Se deduce de las lecturas guardadas en Room, no se almacena: así la línea
 * de eventos siempre coincide con la gráfica y no puede mostrar nada que el
 * estuche no haya reportado.
 *
 * Los eventos de cierre electrónico y acceso por PIN del diseño no están
 * aquí porque dependen de características GATT propias que aún no existen.
 */
sealed interface HistoryEvent {
    /** Momento en que ocurrió el evento (epoch ms). */
    val timestamp: Long

    /**
     * La insulina salió del rango seguro 2–8 °C.
     *
     * @param timestamp primera lectura fuera de rango del episodio.
     * @param peakCelsius valor más alejado del rango durante el episodio
     * (el "Pico de 8.1 °C" del diseño).
     * @param endTimestamp última lectura fuera de rango, o null si el episodio
     * sigue abierto en la lectura más reciente.
     */
    data class TemperatureAlert(
        override val timestamp: Long,
        val direction: TemperatureExcursion,
        val peakCelsius: Float,
        val endTimestamp: Long?
    ) : HistoryEvent

    /**
     * La temperatura volvió al rango seguro después de una alerta.
     *
     * @param celsius temperatura de la primera lectura ya dentro del rango.
     * @param recoveredFrom tipo de alerta de la que se recuperó.
     */
    data class TemperatureNormalized(
        override val timestamp: Long,
        val celsius: Float,
        val recoveredFrom: TemperatureExcursion
    ) : HistoryEvent

    /**
     * La batería del estuche bajó del umbral: la refrigeración puede fallar.
     *
     * @param levelPercent nivel reportado al cruzar el umbral (0–100).
     */
    data class BatteryLow(
        override val timestamp: Long,
        val levelPercent: Int
    ) : HistoryEvent
}

/** Sentido en que la temperatura salió del rango seguro. */
enum class TemperatureExcursion {
    /** Por encima del máximo seguro (8 °C). */
    HIGH,

    /** Por debajo del mínimo seguro (2 °C). */
    LOW
}
