package com.example.medicaseclinico.domain.model

/**
 * Filtros de la pantalla "Alertas de Seguridad", uno por chip del diseño.
 *
 * No coinciden uno a uno con [AlertType]: batería y Bluetooth no tienen chip
 * propio y solo aparecen en [ALL]. [SECURITY] es el registro de accesos del
 * teclado: PIN incorrectos, bloqueos y aperturas correctas.
 */
enum class AlertFilter {
    ALL,
    TEMPERATURE,
    LOCATION,
    SECURITY;

    /** true si una alerta de tipo [type] debe verse con este filtro. */
    fun matches(type: AlertType): Boolean = when (this) {
        ALL -> true
        TEMPERATURE -> type == AlertType.TEMPERATURE
        LOCATION -> type == AlertType.GEOFENCE
        SECURITY -> type == AlertType.SECURITY || type == AlertType.ACCESS_GRANTED
    }

    companion object {
        val DEFAULT = ALL

        /**
         * Chips que ve [access]: un invitado sin permiso para un dato no ve el
         * filtro de sus alertas. "Todas" existe siempre.
         */
        fun visibleFor(access: CaseAccess): List<AlertFilter> = entries.filter { filter ->
            when (filter) {
                ALL -> true
                TEMPERATURE -> access.canSeeAlert(AlertType.TEMPERATURE)
                LOCATION -> access.canSeeAlert(AlertType.GEOFENCE)
                SECURITY -> access.canSeeAlert(AlertType.SECURITY)
            }
        }
    }
}
