package com.example.medicaseclinico.domain.model

/**
 * Lo que el dueño ha pedido en el Modo Pérdida.
 *
 * Se guarda en el teléfono para que sobreviva a cerrar la app: un estuche
 * perdido sigue perdido aunque el teléfono se reinicie. Es la **intención**
 * del dueño, no el estado del estuche: si no hay enlace Bluetooth, la alarma
 * pedida aquí todavía no suena.
 *
 * @param activatedAt momento en que se activó (epoch ms), o null si no está activo.
 * @param alarmRequested el dueño pidió que suene la alarma del estuche.
 * @param highFrequencyGpsRequested el dueño pidió posiciones más seguidas,
 * a cambio de más consumo.
 */
data class LossModeState(
    val isActive: Boolean = false,
    val activatedAt: Long? = null,
    val alarmRequested: Boolean = false,
    val highFrequencyGpsRequested: Boolean = false
) {
    /** Activa el modo. Si ya lo estaba, conserva la hora original. */
    fun activated(now: Long): LossModeState =
        if (isActive) this else copy(isActive = true, activatedAt = now)

    /**
     * Desactiva el modo y con él sus ayudas: la alarma y el GPS rápido solo
     * tienen sentido mientras se busca el estuche.
     */
    fun deactivated(): LossModeState = LossModeState()

    /** Las ayudas solo se pueden pedir con el modo activo. */
    fun withAlarm(requested: Boolean): LossModeState =
        if (isActive) copy(alarmRequested = requested) else this

    fun withHighFrequencyGps(requested: Boolean): LossModeState =
        if (isActive) copy(highFrequencyGpsRequested = requested) else this
}
