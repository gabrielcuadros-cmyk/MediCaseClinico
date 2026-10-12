package com.example.medicaseclinico.domain.model

/**
 * Tiempo transcurrido desde un evento, en la unidad que se muestra al usuario
 * ("Hace 5 min", "Hace 3 horas").
 *
 * Es un tipo y no un texto para que la UI lo traduzca con sus recursos y sus
 * plurales, y para poder probar los cortes entre unidades sin Android.
 */
sealed interface RelativeTime {

    /** Menos de un minuto. */
    data object JustNow : RelativeTime

    data class Minutes(val count: Int) : RelativeTime

    data class Hours(val count: Int) : RelativeTime

    data class Days(val count: Int) : RelativeTime

    companion object {
        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 60 * MINUTE_MS
        private const val DAY_MS = 24 * HOUR_MS

        /**
         * Redondea siempre hacia abajo: 59 s es "ahora" y 119 min es "1 hora".
         * Un [timestamp] posterior a [now] (el usuario atrasó el reloj) también
         * es "ahora", nunca un tiempo negativo.
         */
        fun between(timestamp: Long, now: Long): RelativeTime {
            val elapsed = now - timestamp
            return when {
                elapsed < MINUTE_MS -> JustNow
                elapsed < HOUR_MS -> Minutes((elapsed / MINUTE_MS).toInt())
                elapsed < DAY_MS -> Hours((elapsed / HOUR_MS).toInt())
                else -> Days((elapsed / DAY_MS).toInt())
            }
        }
    }
}
