package com.example.medicaseclinico.data.mqtt

import com.example.medicaseclinico.domain.model.CaseCommandError
import com.example.medicaseclinico.domain.model.CaseCommandException
import com.example.medicaseclinico.domain.model.ConnectionStatus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lo que el repositorio MQTT decide antes de tocar la red. Sin [startMonitoring]
 * no se crea ningún cliente: estos tests no necesitan un broker.
 */
class MqttCaseDeviceRepositoryTest {

    private val clientIdStore = object : MqttClientIdStore {
        override suspend fun getOrCreate(): Result<String> = Result.success("mcapp0123456789abcdef")
    }

    private fun repository(useTls: Boolean) = MqttCaseDeviceRepository(
        config = MqttConfig.parse(
            host = "broker.ejemplo.test",
            port = "",
            useTls = useTls.toString(),
            topicPrefix = "Ana/prueba",
            username = "",
            password = "",
            caseId = ""
        )!!,
        clientIdStore = clientIdStore,
        scope = TestScope()
    )

    private fun Result<Unit>.error(): CaseCommandError? =
        (exceptionOrNull() as? CaseCommandException)?.error

    @Test
    fun `sin tls el pin y el seguro no se envian`() = runTest {
        val repository = repository(useTls = false)

        assertEquals(
            CaseCommandError.SECURE_CONNECTION_REQUIRED,
            repository.changePin(currentPin = "1234", newPin = "8052").error()
        )
        assertEquals(
            CaseCommandError.SECURE_CONNECTION_REQUIRED,
            repository.setPinRequired(required = false, currentPin = "1234").error()
        )
    }

    @Test
    fun `sin tls la alarma y el gps no se bloquean por seguridad`() = runTest {
        val repository = repository(useTls = false)

        // Sin conexión fallan por eso, no por falta de cifrado.
        assertEquals(CaseCommandError.NOT_CONNECTED, repository.setAlarm(on = true).error())
        assertEquals(CaseCommandError.NOT_CONNECTED, repository.setGpsIntervalSeconds(1).error())
    }

    @Test
    fun `con tls y sin conexion las ordenes de pin fallan por falta de enlace`() = runTest {
        val repository = repository(useTls = true)

        assertEquals(
            CaseCommandError.NOT_CONNECTED,
            repository.changePin(currentPin = "1234", newPin = "8052").error()
        )
    }

    @Test
    fun `antes de empezar el monitoreo esta desconectado`() {
        assertEquals(ConnectionStatus.DISCONNECTED, repository(useTls = false).connectionStatus.value)
    }
}
