package com.example.medicaseclinico.domain.model

/**
 * Alerta emitida por la app y guardada para la pantalla "Alertas de Seguridad".
 *
 * Se guarda el tipo y el valor medido, no el texto: así el mensaje se arma
 * al mostrarlo y puede traducirse o corregirse sin tocar los datos antiguos.
 *
 * @param timestamp momento en que se emitió la alerta (epoch ms).
 * @param value dato que acompaña a la alerta según su [type]: temperatura en
 * °C o batería en %. null si el tipo no lleva valor (p. ej. desconexión).
 * @param isRead false hasta que el usuario abre la lista de alertas; alimenta
 * el contador de la pestaña Alertas.
 * @param detail dato de texto que acompaña a la alerta: el nombre de la zona en
 * una alerta de geocerca. Se copia el nombre, no el id de la zona, para que la
 * alerta siga diciendo de dónde salió el estuche aunque la zona se renombre o
 * se borre después. null si el tipo no lo necesita.
 */
data class AlertEvent(
    val id: Long = 0L,
    val type: AlertType,
    val timestamp: Long,
    val value: Float? = null,
    val isRead: Boolean = false,
    val detail: String? = null
)
