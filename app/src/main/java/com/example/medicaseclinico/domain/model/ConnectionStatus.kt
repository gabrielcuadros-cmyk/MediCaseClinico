package com.example.medicaseclinico.domain.model

/**
 * Estado del enlace Bluetooth Low Energy entre el teléfono y el estuche MediCase.
 *
 * Es un concepto de negocio, no de interfaz: aquí no hay colores ni textos.
 * La UI decide cómo pintar cada estado en la TopAppBar,
 * por ejemplo "● Conectado" en verde, como en el diseño del Dashboard.
 */
enum class ConnectionStatus {
    /** Escaneando o negociando el enlace GATT con el estuche. */
    CONNECTING,

    /** Enlace activo: el estuche está enviando temperatura y batería. */
    CONNECTED,

    /**
     * Sin enlace: Bluetooth apagado, estuche fuera de alcance o desconexión
     * inesperada. Corresponde a la alerta "Desconexión Bluetooth" del diseño.
     */
    DISCONNECTED;

    /** true solo si los datos que llegan del estuche son en tiempo real. */
    val isLive: Boolean
        get() = this == CONNECTED

    /**
     * true si el ViewModel debe intentar reconectar automáticamente.
     * Evita lanzar un nuevo intento mientras otro sigue en curso (CONNECTING),
     * lo que ahorra batería del teléfono y del estuche.
     */
    val shouldRetryConnection: Boolean
        get() = this == DISCONNECTED
}