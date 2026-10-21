package com.example.medicaseclinico.data.mqtt

import com.example.medicaseclinico.domain.model.AccessEvent
import com.example.medicaseclinico.domain.model.AccessEventType
import com.example.medicaseclinico.domain.model.CaseCommandError
import com.example.medicaseclinico.domain.model.CaseControlState
import com.example.medicaseclinico.domain.model.CaseGpsReading
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.CaseTelemetry
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.DeviceIssue
import com.example.medicaseclinico.domain.model.LockState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MqttCaseLinkTest {

    private val link = MqttCaseLink()

    private fun online() = link.onMessage(MqttTopic.STATE, """{"online": true}""", 0L)

    private fun offline() = link.onMessage(MqttTopic.STATE, """{"online": false}""", 0L)

    @Test
    fun `conectado al broker no es conectado al estuche`() {
        link.onBrokerConnecting()
        assertEquals(ConnectionStatus.CONNECTING, link.connectionStatus.value)

        link.onBrokerConnected()
        assertEquals(ConnectionStatus.CONNECTING, link.connectionStatus.value)
        assertFalse(link.isCaseOnline)

        online()
        assertEquals(ConnectionStatus.CONNECTED, link.connectionStatus.value)
        assertNull(link.deviceIssue.value)
        assertTrue(link.isCaseOnline)
    }

    @Test
    fun `estuche offline o lwt es desconectado`() {
        link.onBrokerConnected()
        online()

        offline()

        assertEquals(ConnectionStatus.DISCONNECTED, link.connectionStatus.value)
        assertEquals(DeviceIssue.DEVICE_NOT_FOUND, link.deviceIssue.value)
        assertEquals(CaseControlState.Unavailable, link.controlState.value)
        assertEquals(CaseLocationState.Unavailable, link.caseLocation.value)
    }

    @Test
    fun `sin estado publicado tras la espera se da por desconectado`() {
        link.onBrokerConnected()

        link.onCaseStateMissing()

        assertEquals(ConnectionStatus.DISCONNECTED, link.connectionStatus.value)
        assertEquals(DeviceIssue.DEVICE_NOT_FOUND, link.deviceIssue.value)
    }

    @Test
    fun `la espera vencida no afecta a un estuche ya online`() {
        link.onBrokerConnected()
        online()

        link.onCaseStateMissing()

        assertEquals(ConnectionStatus.CONNECTED, link.connectionStatus.value)
    }

    @Test
    fun `perder el broker deja todo sin dato con aviso de conexion perdida`() {
        link.onBrokerConnected()
        online()
        link.onMessage(MqttTopic.LOCATION, """{"fix": true, "lat": 1.5, "lng": 2.5}""", 10L)

        link.onBrokerDisconnected()

        assertEquals(ConnectionStatus.DISCONNECTED, link.connectionStatus.value)
        assertEquals(DeviceIssue.CONNECTION_LOST, link.deviceIssue.value)
        assertEquals(CaseLocationState.Unavailable, link.caseLocation.value)
        assertEquals(CaseControlState.Unavailable, link.controlState.value)
    }

    @Test
    fun `detener el monitoreo no es un fallo`() {
        link.onBrokerConnected()
        online()

        link.onStopped()

        assertEquals(ConnectionStatus.DISCONNECTED, link.connectionStatus.value)
        assertNull(link.deviceIssue.value)
    }

    @Test
    fun `la temperatura solo cuenta con el estuche online`() = runTest {
        val received = mutableListOf<CaseTelemetry>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            link.telemetry.toList(received)
        }
        link.onBrokerConnected()

        link.onMessage(MqttTopic.TEMPERATURE, """{"celsius": 3.0}""", 5L)
        online()
        link.onMessage(MqttTopic.TEMPERATURE, """{"celsius": 4.2}""", 6L)
        offline()
        link.onMessage(MqttTopic.TEMPERATURE, """{"celsius": 9.9}""", 7L)

        assertEquals(listOf(CaseTelemetry.Temperature(4.2f, 6L)), received)
    }

    @Test
    fun `la bateria retenida antes del estado se aplica al confirmarse online`() = runTest {
        val received = mutableListOf<CaseTelemetry>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            link.telemetry.toList(received)
        }
        link.onBrokerConnected()

        link.onMessage(MqttTopic.BATTERY, """{"porcentaje": 78}""", 1L)
        assertTrue(received.isEmpty())

        link.onMessage(MqttTopic.STATE, """{"online": true}""", 2L)

        assertEquals(listOf(CaseTelemetry.Battery(78, 2L)), received)
    }

    @Test
    fun `datos retenidos con el estuche offline se descartan`() = runTest {
        val received = mutableListOf<CaseTelemetry>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            link.telemetry.toList(received)
        }
        link.onBrokerConnected()
        offline()
        link.onMessage(MqttTopic.BATTERY, """{"porcentaje": 78}""", 1L)
        link.onMessage(MqttTopic.LOCK, """{"cierre": "abierto", "seguro": false}""", 1L)

        online()

        assertTrue(received.isEmpty())
        assertEquals(CaseControlState.Ready(lockState = null, pinRequired = null), link.controlState.value)
    }

    @Test
    fun `el cierre retenido antes del estado se aplica al confirmarse online`() {
        link.onBrokerConnected()
        link.onMessage(MqttTopic.LOCK, """{"cierre": "cerrado", "seguro": true}""", 1L)
        assertEquals(CaseControlState.Unavailable, link.controlState.value)

        online()

        assertEquals(
            CaseControlState.Ready(lockState = LockState.LOCKED, pinRequired = true),
            link.controlState.value
        )
    }

    @Test
    fun `un cierre parcial conserva el otro campo`() {
        link.onBrokerConnected()
        online()
        link.onMessage(MqttTopic.LOCK, """{"cierre": "cerrado", "seguro": true}""", 1L)

        link.onMessage(MqttTopic.LOCK, """{"cierre": "abierto"}""", 2L)

        assertEquals(
            CaseControlState.Ready(lockState = LockState.OPEN, pinRequired = true),
            link.controlState.value
        )
    }

    @Test
    fun `ubicacion con fix y sin senal`() {
        link.onBrokerConnected()
        online()

        link.onMessage(MqttTopic.LOCATION, """{"fix": true, "lat": -16.4, "lng": -71.5}""", 3L)
        assertEquals(
            CaseLocationState.Reported(CaseGpsReading.Fix(-16.4, -71.5), 3L),
            link.caseLocation.value
        )

        link.onMessage(MqttTopic.LOCATION, """{"fix": false}""", 4L)
        assertEquals(
            CaseLocationState.Reported(CaseGpsReading.NoSignal, 4L),
            link.caseLocation.value
        )
    }

    @Test
    fun `ubicacion sin estuche online se ignora`() {
        link.onBrokerConnected()

        link.onMessage(MqttTopic.LOCATION, """{"fix": true, "lat": -16.4, "lng": -71.5}""", 3L)

        assertEquals(CaseLocationState.Unavailable, link.caseLocation.value)
    }

    @Test
    fun `eventos de acceso con la hora de recepcion`() = runTest {
        val received = mutableListOf<AccessEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            link.accessEvents.toList(received)
        }
        link.onBrokerConnected()
        link.onMessage(MqttTopic.ACCESS, """{"tipo": "denegado", "intentosFallidos": 1}""", 1L)
        online()

        link.onMessage(MqttTopic.ACCESS, """{"tipo": "bloqueo", "intentosFallidos": 3}""", 9L)

        assertEquals(listOf(AccessEvent(AccessEventType.LOCKED_OUT, 3, 9L)), received)
    }

    @Test
    fun `estado online repetido no reinicia el cierre`() {
        link.onBrokerConnected()
        online()
        link.onMessage(MqttTopic.LOCK, """{"cierre": "abierto"}""", 1L)

        online()

        assertEquals(
            CaseControlState.Ready(lockState = LockState.OPEN, pinRequired = null),
            link.controlState.value
        )
    }

    @Test
    fun `una reconexion al broker olvida lo de la conexion anterior`() {
        link.onBrokerConnected()
        online()
        link.onMessage(MqttTopic.LOCK, """{"cierre": "abierto"}""", 1L)

        link.onBrokerDisconnected()
        link.onBrokerConnected()
        online()

        assertEquals(CaseControlState.Ready(lockState = null, pinRequired = null), link.controlState.value)
    }

    @Test
    fun `mensajes ilegibles o de topics desconocidos no cambian nada`() {
        link.onBrokerConnected()
        link.onMessage(MqttTopic.STATE, "online", 0L)
        link.onMessage("otro", """{"online": true}""", 0L)

        assertEquals(ConnectionStatus.CONNECTING, link.connectionStatus.value)
    }

    @Test
    fun `respuestas a ordenes llegan aunque el estado no se conozca`() = runTest {
        val received = mutableListOf<MqttCodec.CommandReply>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            link.commandReplies.toList(received)
        }
        link.onBrokerConnected()

        link.onMessage(MqttTopic.ORDER_REPLY, """{"id": "a1", "ok": true}""", 1L)
        link.onMessage(MqttTopic.ORDER_REPLY, """{"id": "a2", "ok": false, "error": "pin_incorrecto"}""", 2L)
        link.onMessage(MqttTopic.ORDER_REPLY, """{"ok": true}""", 3L)

        assertEquals(
            listOf(
                MqttCodec.CommandReply(commandId = "a1", error = null),
                MqttCodec.CommandReply(commandId = "a2", error = CaseCommandError.WRONG_PIN)
            ),
            received
        )
    }
}
