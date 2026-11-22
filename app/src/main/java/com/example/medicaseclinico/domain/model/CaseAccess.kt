package com.example.medicaseclinico.domain.model

/**
 * Qué relación tiene la cuenta con sesión iniciada con el estuche.
 *
 * Decide qué pantallas y acciones ve cada persona. Lo resuelve
 * [com.example.medicaseclinico.domain.usecase.ResolveCaseAccessUseCase] a
 * partir de su invitación (o de no tenerla).
 */
sealed interface CaseAccess {

    /**
     * Dueño del estuche (cualquier cuenta que no fue invitada por otra). Ve y
     * controla todo. [uid] es el dueño de la lista de usuarios autorizados.
     */
    data class Owner(val uid: String) : CaseAccess

    /**
     * Persona invitada por el dueño [ownerId]: entra con su propia cuenta y
     * solo puede lo que dicen sus [permissions].
     *
     * @param userId id de su registro en la lista de usuarios autorizados del dueño.
     */
    data class Guest(
        val ownerId: String,
        val userId: String,
        val role: UserRole,
        val permissions: UserPermissions
    ) : CaseAccess

    /** Fue invitada y el dueño le quitó el acceso: no puede ver nada del estuche. */
    data object Revoked : CaseAccess

    /**
     * Uid de quien administra la lista de usuarios autorizados: el propio para
     * el dueño, el del dueño para un invitado; null si el acceso fue revocado.
     */
    val caseOwnerId: String?
        get() = when (this) {
            is Owner -> uid
            is Guest -> ownerId
            Revoked -> null
        }

    /** true si esta cuenta puede [capability]. */
    fun can(capability: CaseCapability): Boolean = when (this) {
        is Owner -> true
        Revoked -> false
        is Guest -> when (capability) {
            CaseCapability.VIEW_TEMPERATURE -> permissions.monitorTemperature
            CaseCapability.VIEW_LOCATION -> permissions.viewLocation
            CaseCapability.VIEW_ACCESS_LOGS -> permissions.viewAccessLogs
            CaseCapability.RECEIVE_ALERTS -> permissions.receiveCriticalAlerts
            CaseCapability.EXPORT_REPORTS -> permissions.exportReports
            CaseCapability.MANAGE_USERS -> permissions.manageUsers
            // Lo que cambia el estuche físico queda siempre en manos del dueño.
            CaseCapability.CONTROL_CASE -> false
        }
    }

    /** true si puede ver alertas de [type] (cada una depende del permiso de su dato). */
    fun canSeeAlert(type: AlertType): Boolean = when (type) {
        AlertType.TEMPERATURE -> can(CaseCapability.VIEW_TEMPERATURE)
        AlertType.GEOFENCE -> can(CaseCapability.VIEW_LOCATION)
        AlertType.SECURITY, AlertType.ACCESS_GRANTED -> can(CaseCapability.VIEW_ACCESS_LOGS)
        AlertType.BATTERY, AlertType.CONNECTION -> this !is Revoked
    }
}

/**
 * Preferencias de notificación ajustadas a lo que [access] puede ver: sin el
 * permiso "Recibir alertas críticas" no llega ninguna, y nunca llega una
 * alerta de un dato que esa cuenta no puede ver. null (acceso aún sin
 * resolver) deja las preferencias como están, para no perder una alerta
 * crítica del dueño durante el arranque.
 */
fun NotificationPreferences.restrictedTo(access: CaseAccess?): NotificationPreferences {
    if (access == null) return this
    val receives = access.can(CaseCapability.RECEIVE_ALERTS)
    return copy(
        temperatureAlertsEnabled = temperatureAlertsEnabled && receives &&
            access.canSeeAlert(AlertType.TEMPERATURE),
        batteryAlertsEnabled = batteryAlertsEnabled && receives && access.canSeeAlert(AlertType.BATTERY),
        connectionAlertsEnabled = connectionAlertsEnabled && receives &&
            access.canSeeAlert(AlertType.CONNECTION),
        geofenceAlertsEnabled = geofenceAlertsEnabled && receives && access.canSeeAlert(AlertType.GEOFENCE),
        securityAlertsEnabled = securityAlertsEnabled && receives && access.canSeeAlert(AlertType.SECURITY)
    )
}

/**
 * Lo que una cuenta puede hacer en la app. Las seis primeras corresponden a
 * los interruptores de "Permisos de Usuario"; [CONTROL_CASE] es solo del dueño.
 */
enum class CaseCapability {
    VIEW_TEMPERATURE,
    VIEW_LOCATION,
    VIEW_ACCESS_LOGS,
    RECEIVE_ALERTS,
    EXPORT_REPORTS,
    MANAGE_USERS,

    /** Cambiar el PIN o el seguro, el Modo Pérdida, la alarma, el GPS y las geocercas. */
    CONTROL_CASE
}

/**
 * Invitación guardada por correo: une la cuenta que entra con ese correo al
 * registro que el dueño creó en su lista de usuarios autorizados.
 */
data class Invitation(
    val ownerId: String,
    val userId: String
)
