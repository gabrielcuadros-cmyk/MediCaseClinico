package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.AccessEvent
import com.example.medicaseclinico.domain.model.CaseControlState
import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.CaseTelemetry
import com.example.medicaseclinico.domain.model.CaseTransport
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.DeviceIssue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato de comunicación en tiempo real con el estuche MediCase.
 *
 * Responsabilidades separadas:
 *  - TemperatureRepository → historial persistente (Room).
 *  - CaseDeviceRepository  → enlace vivo con el hardware (MQTT o, de respaldo, BLE).
 *
 * Regla del contrato: ningún método lanza excepciones hacia afuera.
 * Los fallos se reflejan en [connectionStatus] y [deviceIssue]; los de una
 * orden al estuche llegan en su `Result` como
 * [com.example.medicaseclinico.domain.model.CaseCommandException].
 */
interface CaseDeviceRepository {
    /** Por dónde llegan los datos: no cambia mientras la app está abierta. */
    val transport: CaseTransport

    /** Estado real del enlace BLE. */
    val connectionStatus: StateFlow<ConnectionStatus>

    /** Motivo actual por el que no hay enlace, o null si no hay ningún problema. */
    val deviceIssue: StateFlow<DeviceIssue?>

    /** Flujo caliente de mediciones recibidas del estuche. */
    val telemetry: Flow<CaseTelemetry>

    /**
     * Ubicación del estuche según su propio GPS, mientras dure el enlace.
     *
     * Vuelve a [CaseLocationState.Unavailable] al perder la conexión: este
     * flujo solo cuenta lo que el estuche dice ahora. La última ubicación
     * conocida, con su hora, es cosa del historial guardado.
     */
    val caseLocation: StateFlow<CaseLocationState>

    /**
     * Cierre y PIN del estuche, mientras dure el enlace. Vuelve a
     * [CaseControlState.Unavailable] al perder la conexión.
     */
    val controlState: StateFlow<CaseControlState>

    /**
     * Intentos de abrir el estuche desde su teclado, según ocurren. Solo
     * llegan con el enlace activo: el estuche no los guarda para después.
     */
    val accessEvents: Flow<AccessEvent>

    /**
     * Activa o desactiva el seguro (pedir PIN para abrir). Exige el PIN
     * actual: estar emparejado no basta para quitarle la protección al estuche.
     */
    suspend fun setPinRequired(required: Boolean, currentPin: String): Result<Unit>

    /** Cambia el PIN del estuche. La app no guarda ninguno de los dos. */
    suspend fun changePin(currentPin: String, newPin: String): Result<Unit>

    /** Enciende o apaga la alarma sonora (zumbador) del estuche. */
    suspend fun setAlarm(on: Boolean): Result<Unit>

    /** Fija cada cuántos segundos envía su posición el GPS del estuche. */
    suspend fun setGpsIntervalSeconds(seconds: Int): Result<Unit>

    /** Inicia escaneo, conexión y suscripción a notificaciones. */
    fun startMonitoring()

    /** Cierra la conexión GATT y detiene cualquier escaneo en curso. */
    fun stopMonitoring()
}