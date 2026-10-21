package com.example.medicaseclinico.data.mqtt

import com.example.medicaseclinico.domain.model.AccessEvent
import com.example.medicaseclinico.domain.model.CaseControlState
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.CaseTelemetry
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.DeviceIssue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lo que la app sabe del estuche a partir de los mensajes del broker.
 *
 * Separado de la conexión (HiveMQ) para poder probarlo sin red. Su regla central:
 * estar conectado al **broker** no es estar conectado al **estuche**. Solo con
 * `estado` online los datos cuentan como en vivo; antes se descartan o se guardan
 * hasta entonces, porque un valor retenido en el broker puede tener horas.
 *
 * Seguro entre hilos: HiveMQ entrega los mensajes en sus propios hilos.
 */
class MqttCaseLink {

    private val lock = Any()

    /** null: aún no se sabe (no ha llegado `estado` en esta conexión al broker). */
    private var caseOnline: Boolean? = null
    private var brokerConnected = false

    // Valores retenidos que llegan antes que `estado`: se aplican al confirmarse online.
    private var pendingLock: MqttCodec.LockReport? = null
    private var pendingBatteryPercent: Int? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _deviceIssue = MutableStateFlow<DeviceIssue?>(null)
    val deviceIssue: StateFlow<DeviceIssue?> = _deviceIssue.asStateFlow()

    private val _telemetry = MutableSharedFlow<CaseTelemetry>(
        extraBufferCapacity = TELEMETRY_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val telemetry: Flow<CaseTelemetry> = _telemetry.asSharedFlow()

    private val _caseLocation = MutableStateFlow<CaseLocationState>(CaseLocationState.Unavailable)
    val caseLocation: StateFlow<CaseLocationState> = _caseLocation.asStateFlow()

    private val _controlState = MutableStateFlow<CaseControlState>(CaseControlState.Unavailable)
    val controlState: StateFlow<CaseControlState> = _controlState.asStateFlow()

    private val _accessEvents = MutableSharedFlow<AccessEvent>(
        extraBufferCapacity = ACCESS_EVENT_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val accessEvents: Flow<AccessEvent> = _accessEvents.asSharedFlow()

    private val _commandReplies = MutableSharedFlow<MqttCodec.CommandReply>(
        extraBufferCapacity = COMMAND_REPLY_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Respuestas del estuche a las órdenes (`orden/respuesta`). Quien envía una orden
     * espera aquí la que lleva su `id`.
     */
    val commandReplies: Flow<MqttCodec.CommandReply> = _commandReplies.asSharedFlow()

    /** true solo con el estuche online: es cuando acepta órdenes. */
    val isCaseOnline: Boolean
        get() = synchronized(lock) { caseOnline == true }

    /** Primer intento de conexión con el broker. */
    fun onBrokerConnecting() = synchronized(lock) {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _deviceIssue.value = null
    }

    /**
     * Conectado al broker: se espera el `estado` retenido del estuche para saber si
     * está online. Lo de la conexión anterior no vale.
     */
    fun onBrokerConnected() = synchronized(lock) {
        brokerConnected = true
        caseOnline = null
        pendingLock = null
        pendingBatteryPercent = null
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _deviceIssue.value = null
    }

    /** Se perdió (o no se logró) la conexión con el broker; HiveMQ reintenta solo. */
    fun onBrokerDisconnected() = synchronized(lock) {
        brokerConnected = false
        caseOnline = null
        clearCase(issue = DeviceIssue.CONNECTION_LOST)
    }

    /** Monitoreo detenido a propósito: no es un fallo, no hay aviso. */
    fun onStopped() = synchronized(lock) {
        brokerConnected = false
        caseOnline = null
        clearCase(issue = null)
    }

    /**
     * Pasó el tiempo de espera tras conectar y el estuche nunca publicó `estado`:
     * se trata como desconectado, no como "conectando" para siempre.
     */
    fun onCaseStateMissing() = synchronized(lock) {
        if (brokerConnected && caseOnline == null) goOffline()
    }

    /**
     * Mensaje recibido.
     *
     * @param topic nombre relativo a la raíz ([MqttTopic]).
     * @param receivedAt hora de recepción: el estuche no tiene reloj.
     */
    fun onMessage(topic: String, payload: String, receivedAt: Long) {
        synchronized(lock) { handleMessage(topic, payload, receivedAt) }
    }

    private fun handleMessage(topic: String, payload: String, receivedAt: Long) {
        when (topic) {
            MqttTopic.STATE -> MqttCodec.parseOnline(payload)?.let { online ->
                if (online) goOnline(receivedAt) else goOffline()
            }
            MqttTopic.TEMPERATURE -> if (caseOnline == true) {
                MqttCodec.parseTemperatureCelsius(payload)?.let { celsius ->
                    _telemetry.tryEmit(CaseTelemetry.Temperature(celsius, receivedAt))
                }
            }
            MqttTopic.BATTERY -> if (caseOnline != false) {
                MqttCodec.parseBatteryPercent(payload)?.let { percent ->
                    if (caseOnline == true) {
                        _telemetry.tryEmit(CaseTelemetry.Battery(percent, receivedAt))
                    } else {
                        pendingBatteryPercent = percent
                    }
                }
            }
            MqttTopic.LOCATION -> if (caseOnline == true) {
                MqttCodec.parseLocation(payload)?.let { reading ->
                    _caseLocation.value = CaseLocationState.Reported(reading, receivedAt)
                }
            }
            MqttTopic.LOCK -> if (caseOnline != false) {
                MqttCodec.parseLock(payload)?.let { report ->
                    val merged = report.mergedOver(pendingLock)
                    pendingLock = merged
                    if (caseOnline == true) _controlState.value = merged.toReadyState()
                }
            }
            MqttTopic.ACCESS -> if (caseOnline == true) {
                MqttCodec.parseAccessEvent(payload, receivedAt)?.let { _accessEvents.tryEmit(it) }
            }
            // Sin filtrar por online: si el estuche respondió, la orden se ejecutó o no.
            MqttTopic.ORDER_REPLY ->
                MqttCodec.parseCommandReply(payload)?.let { _commandReplies.tryEmit(it) }
        }
    }

    private fun goOnline(receivedAt: Long) {
        // El retenido y el que publica el estuche al conectar pueden llegar los dos.
        if (caseOnline == true) return
        caseOnline = true
        _connectionStatus.value = ConnectionStatus.CONNECTED
        _deviceIssue.value = null
        _controlState.value = (pendingLock ?: MqttCodec.LockReport(null, null)).toReadyState()
        pendingBatteryPercent?.let { _telemetry.tryEmit(CaseTelemetry.Battery(it, receivedAt)) }
        pendingBatteryPercent = null
    }

    private fun goOffline() {
        caseOnline = false
        clearCase(issue = DeviceIssue.DEVICE_NOT_FOUND)
    }

    private fun clearCase(issue: DeviceIssue?) {
        pendingLock = null
        pendingBatteryPercent = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _deviceIssue.value = issue
        _caseLocation.value = CaseLocationState.Unavailable
        _controlState.value = CaseControlState.Unavailable
    }

    // El estuche puede publicar solo el campo que cambió: lo demás se conserva.
    private fun MqttCodec.LockReport.mergedOver(previous: MqttCodec.LockReport?) =
        MqttCodec.LockReport(
            lockState = lockState ?: previous?.lockState,
            pinRequired = pinRequired ?: previous?.pinRequired
        )

    private fun MqttCodec.LockReport.toReadyState() =
        CaseControlState.Ready(lockState = lockState, pinRequired = pinRequired)

    private companion object {
        const val TELEMETRY_BUFFER_SIZE = 32
        const val ACCESS_EVENT_BUFFER_SIZE = 16
        const val COMMAND_REPLY_BUFFER_SIZE = 16
    }
}
