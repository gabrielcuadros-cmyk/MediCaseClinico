package com.example.medicaseclinico.domain.alert

import com.example.medicaseclinico.domain.model.AlertEvent
import com.example.medicaseclinico.domain.model.AlertType
import com.example.medicaseclinico.domain.model.BatteryThresholds
import com.example.medicaseclinico.domain.model.Geofence
import com.example.medicaseclinico.domain.model.TemperatureStatus
import com.example.medicaseclinico.domain.repository.AlertEventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Guarda en el historial cada alerta nueva y después la pasa al emisor real.
 *
 * Envuelve a otro [AlertPublisher] en lugar de vivir dentro del emisor de
 * notificaciones porque son decisiones distintas: una alerta clínica debe
 * quedar registrada aunque el usuario haya silenciado las notificaciones,
 * esté en horario de no molestar o haya revocado el permiso.
 *
 * Registra una fila por episodio, no por lectura: mientras la temperatura
 * siga fuera de rango, la batería siga baja o el estuche siga desconectado,
 * no se repite la alerta.
 *
 * @param scope ámbito de la app para guardar sin bloquear la telemetría BLE.
 * @param clock se inyecta para que los tests fijen la hora de la alerta.
 */
class RecordingAlertPublisher(
    private val delegate: AlertPublisher,
    private val alertEventRepository: AlertEventRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) : AlertPublisher {

    private var temperatureAlertActive = false
    private var lowBatteryAlertActive = false
    private var hasBeenConnected = false
    private var disconnectionAlertActive = false

    @Synchronized
    override fun onTemperature(celsius: Float, status: TemperatureStatus) {
        if (status.isAlert && !temperatureAlertActive) {
            record(type = AlertType.TEMPERATURE, value = celsius)
        }
        temperatureAlertActive = status.isAlert
        delegate.onTemperature(celsius, status)
    }

    /** Misma histéresis que la notificación: no vuelve a avisar hasta recuperarse. */
    @Synchronized
    override fun onBatteryLevel(percent: Int) {
        when {
            percent < BatteryThresholds.LOW_PERCENT && !lowBatteryAlertActive -> {
                record(type = AlertType.BATTERY, value = percent.toFloat())
                lowBatteryAlertActive = true
            }
            percent >= BatteryThresholds.REARM_PERCENT -> lowBatteryAlertActive = false
        }
        delegate.onBatteryLevel(percent)
    }

    /** Un estuche que nunca llegó a conectarse no se ha "desconectado". */
    @Synchronized
    override fun onConnectionLost() {
        if (hasBeenConnected && !disconnectionAlertActive) {
            record(type = AlertType.CONNECTION, value = null)
            disconnectionAlertActive = true
        }
        delegate.onConnectionLost()
    }

    @Synchronized
    override fun onConnectionRestored() {
        hasBeenConnected = true
        disconnectionAlertActive = false
        delegate.onConnectionRestored()
    }

    /**
     * Cada salida ya es un episodio (la evaluación de geocercas solo avisa al
     * cruzar el borde), así que se registra siempre, con el nombre de la zona.
     */
    @Synchronized
    override fun onGeofenceExited(geofence: Geofence) {
        record(type = AlertType.GEOFENCE, value = null, detail = geofence.name)
        delegate.onGeofenceExited(geofence)
    }

    /** Volver a la zona no es una alerta: solo se avisa al emisor real. */
    @Synchronized
    override fun onGeofenceEntered(geofence: Geofence) {
        delegate.onGeofenceEntered(geofence)
    }

    /**
     * Si la base de datos falla, la alerta no queda en el historial pero la
     * notificación se emite igual: el `Result` se descarta a propósito.
     */
    private fun record(type: AlertType, value: Float?, detail: String? = null) {
        val event = AlertEvent(type = type, timestamp = clock(), value = value, detail = detail)
        scope.launch { alertEventRepository.recordAlert(event) }
    }
}
