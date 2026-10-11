package com.example.medicaseclinico.domain.model

/**
 * Clasificación clínica de una lectura de temperatura del estuche.
 *
 * Rango de conservación de la insulina sin abrir: 2 °C – 8 °C
 * (el "Rango óptimo" que muestra el Dashboard y la gráfica de estabilidad).
 *
 *  CRITICAL   SAFE...............................SAFE   CRITICAL
 *  ───────┤ 2.0 ├ WARNING ┤ 2.5 ─────── 7.5 ├ WARNING ┤ 8.0 ├───────
 */
enum class TemperatureStatus {
    /** Dentro del rango y lejos de los límites. Tarjeta en verde ("SEGURO"). */
    SAFE,

    /** Dentro del rango pero a menos de 0.5 °C de un límite: tendencia de riesgo. */
    WARNING,

    /**
     * Fuera del rango 2–8 °C. La insulina puede perder eficacia.
     * Dispara la alerta "Temperatura Fuera de Rango" y pinta la tarjeta en rojo.
     */
    CRITICAL;

    /** true si la lectura debe registrarse como alerta en el historial (isAlertTriggered). */
    val isAlert: Boolean
        get() = this == CRITICAL

    companion object {
        /** Límite inferior seguro (°C). */
        const val MIN_SAFE_CELSIUS = 2.0f

        /** Límite superior seguro (°C). */
        const val MAX_SAFE_CELSIUS = 8.0f

        /** Margen antes de cada límite que activa el estado WARNING (°C). */
        const val WARNING_MARGIN_CELSIUS = 0.5f

        /**
         * Clasifica una temperatura en grados Celsius.
         *
         * Política fail-safe: un valor no numérico (NaN o infinito, típico de
         * una trama BLE corrupta) se trata como CRITICAL. En una app clínica es
         * preferible una falsa alarma a ocultar un posible fallo del sensor.
         */
        fun fromCelsius(celsius: Float): TemperatureStatus = when {
            !celsius.isFinite() -> CRITICAL
            celsius < MIN_SAFE_CELSIUS || celsius > MAX_SAFE_CELSIUS -> CRITICAL
            celsius < MIN_SAFE_CELSIUS + WARNING_MARGIN_CELSIUS ||
                    celsius > MAX_SAFE_CELSIUS - WARNING_MARGIN_CELSIUS -> WARNING
            else -> SAFE
        }
    }
}