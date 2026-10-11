package com.example.medicaseclinico.domain.model

/**
 * Umbrales de batería del estuche, definidos una sola vez.
 *
 * Los comparten la notificación, la tarjeta del Dashboard y la línea de
 * eventos: si cada uno tuviera su propio valor, el usuario podría ver la
 * batería "baja" en una pantalla y normal en otra.
 */
object BatteryThresholds {
    /** Por debajo de este nivel la batería es baja (ajuste "Batería Baja (20 %)" del diseño). */
    const val LOW_PERCENT = 20

    /**
     * Nivel a partir del cual se considera recuperada y se puede volver a
     * avisar. Es mayor que [LOW_PERCENT] para que una batería que oscila
     * alrededor del umbral no genere avisos repetidos (histéresis).
     */
    const val REARM_PERCENT = 25
}
