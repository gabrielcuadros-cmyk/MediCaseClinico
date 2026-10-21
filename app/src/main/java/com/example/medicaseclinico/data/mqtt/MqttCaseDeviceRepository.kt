package com.example.medicaseclinico.data.mqtt

import com.example.medicaseclinico.domain.model.AccessEvent
import com.example.medicaseclinico.domain.model.CaseCommandError
import com.example.medicaseclinico.domain.model.CaseCommandException
import com.example.medicaseclinico.domain.model.CaseControlState
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.CaseTelemetry
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.DeviceIssue
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Enlace con el estuche a través de un broker MQTT (ver hardware/mqtt-protocol.md).
 *
 * El estuche publica por Wi-Fi y la app se suscribe: no hace falta estar cerca de él
 * ni pedir permisos de Bluetooth. Cumple el mismo contrato que la implementación BLE,
 * de modo que el resto de la app (sincronizador, alertas, pantallas) no cambia.
 *
 * La conexión la gestiona HiveMQ con reconexión automática; lo que significa cada
 * mensaje lo decide [MqttCaseLink].
 */
class MqttCaseDeviceRepository(
    private val config: MqttConfig,
    private val clientIdStore: MqttClientIdStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) : CaseDeviceRepository {

    private val link = MqttCaseLink()

    override val connectionStatus: StateFlow<ConnectionStatus> = link.connectionStatus
    override val deviceIssue: StateFlow<DeviceIssue?> = link.deviceIssue
    override val telemetry: Flow<CaseTelemetry> = link.telemetry
    override val caseLocation: StateFlow<CaseLocationState> = link.caseLocation
    override val controlState: StateFlow<CaseControlState> = link.controlState
    override val accessEvents: Flow<AccessEvent> = link.accessEvents

    private val topicRoot = config.topicBase + "/"

    private var monitoringJob: Job? = null
    private var caseStateWaitJob: Job? = null
    @Volatile private var client: Mqtt3AsyncClient? = null

    // Cada monitoreo tiene su número: los avisos de un cliente ya cerrado se ignoran.
    @Volatile private var session = 0

    override fun startMonitoring() {
        if (monitoringJob?.isActive == true) return
        val current = ++session
        monitoringJob = scope.launch { runMonitoring(current) }
    }

    override fun stopMonitoring() {
        session++
        monitoringJob?.cancel()
        monitoringJob = null
        caseStateWaitJob?.cancel()
        disconnectQuietly(client)
        client = null
        link.onStopped()
    }

    // Las órdenes por MQTT llegan con orden/respuesta; hasta entonces no se envían.
    override suspend fun setPinRequired(required: Boolean, currentPin: String): Result<Unit> =
        notSupported()

    override suspend fun changePin(currentPin: String, newPin: String): Result<Unit> =
        notSupported()

    override suspend fun setAlarm(on: Boolean): Result<Unit> = notSupported()

    override suspend fun setGpsIntervalSeconds(seconds: Int): Result<Unit> = notSupported()

    private fun notSupported(): Result<Unit> =
        Result.failure(CaseCommandException(CaseCommandError.NOT_SUPPORTED))

    private suspend fun runMonitoring(current: Int) {
        link.onBrokerConnecting()
        try {
            // Sin almacenamiento disponible se usa un ID de un solo uso: sigue siendo único.
            val clientId = clientIdStore.getOrCreate().getOrElse { MqttClientId.newAppClientId() }
            val mqtt = buildClient(clientId, current)
            client = mqtt
            mqtt.publishes(MqttGlobalPublishFilter.SUBSCRIBED) { publish -> onPublish(publish, current) }
            try {
                mqtt.connectWith()
                    .cleanSession(true)
                    .keepAlive(KEEP_ALIVE_SECONDS)
                    .send()
                    .await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // HiveMQ ya está reintentando (el aviso de desconexión lo refleja). Si no
                // reintenta, se termina para que "Reintentar" pueda empezar de nuevo.
                if (!mqtt.state.isConnectedOrReconnect) {
                    link.onBrokerDisconnected()
                    return
                }
            }
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Configuración que HiveMQ rechaza al construir el cliente, por ejemplo.
            link.onBrokerDisconnected()
        }
    }

    private fun buildClient(clientId: String, current: Int): Mqtt3AsyncClient {
        var builder = MqttClient.builder()
            .useMqttVersion3()
            .identifier(clientId)
            .serverHost(config.host)
            .serverPort(config.port)
            .automaticReconnect()
            .initialDelay(RECONNECT_INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
            .maxDelay(RECONNECT_MAX_DELAY_SECONDS, TimeUnit.SECONDS)
            .applyAutomaticReconnect()
            .addConnectedListener { if (session == current) onBrokerConnected(current) }
            .addDisconnectedListener {
                if (session == current) {
                    caseStateWaitJob?.cancel()
                    link.onBrokerDisconnected()
                }
            }
        if (config.useTls) builder = builder.sslWithDefaultConfig()
        config.username?.let { username ->
            val auth = Mqtt3SimpleAuth.builder().username(username)
            val password = config.password
            val complete =
                if (password == null) auth else auth.password(password.toByteArray(Charsets.UTF_8))
            builder = builder.simpleAuth(complete.build())
        }
        return builder.buildAsync()
    }

    private fun onBrokerConnected(current: Int) {
        link.onBrokerConnected()
        // Sesión limpia: en cada conexión hay que suscribirse de nuevo.
        scope.launch { subscribe(current) }
        caseStateWaitJob?.cancel()
        caseStateWaitJob = scope.launch {
            delay(CASE_STATE_TIMEOUT_MS)
            if (session == current) link.onCaseStateMissing()
        }
    }

    private suspend fun subscribe(current: Int) {
        val subscribe = Mqtt3Subscribe.builder()
            .addSubscriptions(
                SUBSCRIPTIONS.map { (topic, qos) ->
                    Mqtt3Subscription.builder().topicFilter(config.topic(topic)).qos(qos).build()
                }
            )
            .build()
        var delayMs = SUBSCRIBE_RETRY_INITIAL_MS
        while (scope.isActive && session == current) {
            val mqtt = client ?: return
            if (!mqtt.state.isConnected) return
            try {
                mqtt.subscribe(subscribe).await()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(SUBSCRIBE_RETRY_MAX_MS)
            }
        }
    }

    private fun onPublish(publish: Mqtt3Publish, current: Int) {
        if (session != current) return
        try {
            val topic = publish.topic.toString()
            if (!topic.startsWith(topicRoot)) return
            val payload = String(publish.payloadAsBytes, Charsets.UTF_8)
            link.onMessage(topic.removePrefix(topicRoot), payload, clock())
        } catch (e: Exception) {
            // Un mensaje ilegible se descarta: nunca debe tumbar el hilo de HiveMQ.
        }
    }

    private fun disconnectQuietly(mqtt: Mqtt3AsyncClient?) {
        if (mqtt == null) return
        try {
            // Desconexión ordenada: además detiene la reconexión automática.
            mqtt.disconnect()
        } catch (e: Exception) {
            // Manejado: el cliente se descarta igualmente.
        }
    }

    private companion object {
        const val KEEP_ALIVE_SECONDS = 30
        const val RECONNECT_INITIAL_DELAY_SECONDS = 1L
        const val RECONNECT_MAX_DELAY_SECONDS = 60L

        // Tras conectar, el `estado` retenido llega enseguida; si no, el estuche nunca
        // lo publicó y se considera desconectado.
        const val CASE_STATE_TIMEOUT_MS = 10_000L

        const val SUBSCRIBE_RETRY_INITIAL_MS = 1_000L
        const val SUBSCRIBE_RETRY_MAX_MS = 30_000L

        /** Lo que publica el estuche, con el QoS de hardware/mqtt-protocol.md §2. */
        val SUBSCRIPTIONS = listOf(
            MqttTopic.STATE to MqttQos.AT_LEAST_ONCE,
            MqttTopic.TEMPERATURE to MqttQos.AT_MOST_ONCE,
            MqttTopic.BATTERY to MqttQos.AT_LEAST_ONCE,
            MqttTopic.LOCATION to MqttQos.AT_MOST_ONCE,
            MqttTopic.LOCK to MqttQos.AT_LEAST_ONCE,
            MqttTopic.ACCESS to MqttQos.AT_LEAST_ONCE
        )
    }
}
