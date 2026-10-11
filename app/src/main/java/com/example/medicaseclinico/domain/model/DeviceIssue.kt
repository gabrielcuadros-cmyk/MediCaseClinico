package com.example.medicaseclinico.domain.model

/**
 * Motivo por el que la app no puede comunicarse con el estuche.
 *
 * Es complementario a ConnectionStatus: el estado dice SI hay enlace;
 * DeviceIssue dice POR QUÉ no lo hay, para mostrar al usuario la acción
 * concreta que lo resuelve (activar Bluetooth, conceder permisos, etc.).
 */
enum class DeviceIssue {
    /** El teléfono no tiene hardware Bluetooth LE (típico en emuladores). */
    BLE_NOT_SUPPORTED,

    /** El Bluetooth está apagado. Acción: pedir al usuario que lo active. */
    BLUETOOTH_DISABLED,

    /** Faltan permisos de "Dispositivos cercanos" o ubicación. Acción: solicitarlos. */
    PERMISSIONS_MISSING,

    /** El escaneo terminó sin encontrar un estuche MediCase cercano. */
    DEVICE_NOT_FOUND,

    /** Había enlace y se perdió; se agotaron los reintentos automáticos. */
    CONNECTION_LOST;

    /** true si el usuario puede resolverlo desde la app sin ir a Ajustes del sistema. */
    val isUserActionable: Boolean
        get() = this == BLUETOOTH_DISABLED || this == PERMISSIONS_MISSING
}