package com.example.medicaseclinico.ui.screens.dashboard

import androidx.compose.runtime.Immutable
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.TemperatureStatus

/**
 * Estado único e inmutable de la pantalla Dashboard.
 *
 * El ViewModel lo expone como StateFlow<DashboardUiState> y la UI solo lo lee:
 * los Composables no calculan nada, solo dibujan el estado recibido.
 *
 * Todos los estados llevan [connectionStatus] porque la TopAppBar muestra el
 * indicador BLE ("● Conectado") en cualquier situación, incluso con error.
 */
@Immutable
sealed interface DashboardUiState {
    val connectionStatus: ConnectionStatus

    /** Primera carga: aún no hay respuesta de Room. Muestra CircularProgressIndicator. */
    data class Loading(
        override val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTING
    ) : DashboardUiState

    /**
     * Carga correcta pero sin lecturas: estuche recién vinculado o historial vacío.
     * Muestra el Empty State en lugar de inventar valores.
     */
    data class Empty(
        override val connectionStatus: ConnectionStatus
    ) : DashboardUiState

    /** Datos reales disponibles: tarjeta de temperatura, batería y cabecera. */
    data class Success(
        override val connectionStatus: ConnectionStatus,
        /** Última temperatura registrada en Room (ej. 4.2 °C en el diseño). */
        val currentTemperature: Float,
        /** Clasificación calculada por el ViewModel con TemperatureStatus.fromCelsius(). */
        val temperatureStatus: TemperatureStatus,
        /**
         * Último nivel de batería conocido (ej. 78 % en el diseño).
         * null si el estuche aún no lo ha reportado → la tarjeta muestra "--".
         */
        val batteryLevel: Int?,
        /** Marca de tiempo de la última lectura (para "Sincronizado hace 2 min"). */
        val lastReadingTimestamp: Long
    ) : DashboardUiState {
        /**
         * false si el estuche está desconectado: la temperatura mostrada es la
         * última guardada, no una lectura en vivo, y la UI debe indicarlo.
         */
        val isLiveData: Boolean
            get() = connectionStatus.isLive

        /** true si la batería está por debajo del 20 % (alerta "Batería Baja" del diseño). */
        val isBatteryLow: Boolean
            get() = batteryLevel != null && batteryLevel < LOW_BATTERY_THRESHOLD
    }

    /** Fallo controlado. La app no crashea: muestra la pantalla de error con reintento. */
    data class Error(
        override val connectionStatus: ConnectionStatus,
        val reason: ErrorReason,
        val canRetry: Boolean = true
    ) : DashboardUiState

    companion object {
        /** Umbral de batería baja, coincide con el ajuste "Batería Baja (20 %)" del diseño. */
        const val LOW_BATTERY_THRESHOLD = 20
    }
}

/**
 * Causa del error expresada como tipo, no como texto.
 * La UI la traduce a un mensaje de strings.xml, así el ViewModel no necesita
 * un Context y los mensajes se pueden traducir.
 */
enum class ErrorReason {
    /** Fallo al leer o escribir el historial térmico local (Room). */
    DATABASE,
    /** Bluetooth apagado, permisos denegados o pérdida del enlace con el estuche. */
    BLUETOOTH,
    /** Sin conexión a internet al sincronizar con Firebase. */
    NETWORK,
    /** Cualquier otro fallo no previsto. */
    UNKNOWN
}