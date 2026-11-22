package com.example.medicaseclinico.ui.screens.dashboard

import androidx.compose.runtime.Immutable
import com.example.medicaseclinico.domain.model.CaseAccess
import com.example.medicaseclinico.domain.model.CaseCapability
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.RelativeTime
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
        /** Posición del cierre según el estuche, o por qué no se conoce. */
        val lockStatus: CaseLockStatus,
        /** Datos reales de las "Secciones rápidas". */
        val quickSections: QuickSectionsState,
        /**
         * Tiempo desde la última lectura, para el pie "Última lectura recibida
         * vía Bluetooth hace 2 min". Lo calcula el ViewModel para que la UI no
         * consulte el reloj al dibujar.
         */
        val lastReadingElapsed: RelativeTime,
        /**
         * true si, sin enlace, la lectura supera el límite de antigüedad: la UI
         * la atenúa y quita la insignia para no presentarla como estado actual.
         */
        val isReadingOutdated: Boolean
    ) : DashboardUiState {
        /**
         * false si el estuche está desconectado: la temperatura mostrada es la
         * última guardada, no una lectura en vivo, y la UI debe indicarlo.
         */
        val isLiveData: Boolean
            get() = connectionStatus.isLive
    }

    /** Fallo controlado. La app no crashea: muestra la pantalla de error con reintento. */
    data class Error(
        override val connectionStatus: ConnectionStatus,
        val reason: ErrorReason,
        val canRetry: Boolean = true
    ) : DashboardUiState
}

/** Lo que se sabe del cierre electrónico del estuche, para la tarjeta "Cierre". */
enum class CaseLockStatus {
    LOCKED,
    OPEN,

    /** Sin enlace, o el estuche aún no lo ha informado: la tarjeta muestra "--". */
    UNKNOWN,

    /** El estuche conectado no informa de su cierre. */
    NOT_SUPPORTED
}

/**
 * Lo que enseña cada atajo de "Secciones rápidas".
 *
 * @param lastLocationElapsed cuánto hace de la última posición del estuche (en
 * vivo o guardada), o null si nunca se ha recibido ninguna.
 * @param unreadAlerts alertas que el usuario aún no ha visto.
 */
@Immutable
data class QuickSectionsState(
    val lastLocationElapsed: RelativeTime?,
    val unreadAlerts: Int,
    val pinLock: PinLockStatus
)

/** Si el estuche pide PIN para abrirse, según él mismo. */
enum class PinLockStatus {
    ENABLED,
    DISABLED,

    /** Sin enlace, estuche no compatible o aún sin informar: se muestra "--". */
    UNKNOWN
}

/** Pestañas a las que llevan las "Secciones rápidas". */
enum class DashboardSection { HISTORY, LOCATION, ALERTS, SETTINGS }

/**
 * Qué partes del Dashboard ve la cuenta. El dueño, todas; un invitado, según
 * sus permisos. La decisión es del dominio ([CaseAccess.can]); aquí solo se
 * traduce a lo que la pantalla muestra.
 */
@Immutable
data class DashboardPermissions(
    val showTemperature: Boolean = true,
    val showLocation: Boolean = true,
    val showAccessLog: Boolean = true,
    val canUseLossMode: Boolean = true
) {
    /** Atajos de "Secciones rápidas" que puede usar. */
    val visibleSections: Set<DashboardSection>
        get() = buildSet {
            if (showTemperature) add(DashboardSection.HISTORY)
            if (showLocation) add(DashboardSection.LOCATION)
            add(DashboardSection.ALERTS)
            add(DashboardSection.SETTINGS)
        }

    companion object {
        fun of(access: CaseAccess) = DashboardPermissions(
            showTemperature = access.can(CaseCapability.VIEW_TEMPERATURE),
            showLocation = access.can(CaseCapability.VIEW_LOCATION),
            showAccessLog = access.can(CaseCapability.VIEW_ACCESS_LOGS),
            canUseLossMode = access.can(CaseCapability.CONTROL_CASE)
        )
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