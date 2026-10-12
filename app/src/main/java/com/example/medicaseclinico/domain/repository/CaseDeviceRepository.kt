package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.CaseLocationState
import com.example.medicaseclinico.domain.model.CaseTelemetry
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.DeviceIssue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato de comunicación en tiempo real con el estuche MediCase.
 *
 * Responsabilidades separadas:
 *  - TemperatureRepository → historial persistente (Room).
 *  - CaseDeviceRepository  → enlace vivo con el hardware (BLE).
 *
 * Regla del contrato: ningún método lanza excepciones hacia afuera.
 * Los fallos se reflejan en [connectionStatus] y [deviceIssue].
 */
interface CaseDeviceRepository {
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

    /** Inicia escaneo, conexión y suscripción a notificaciones. */
    fun startMonitoring()

    /** Cierra la conexión GATT y detiene cualquier escaneo en curso. */
    fun stopMonitoring()
}