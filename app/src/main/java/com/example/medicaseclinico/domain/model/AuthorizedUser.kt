package com.example.medicaseclinico.domain.model

/**
 * Papel de una persona respecto al estuche. Decide con qué permisos empieza
 * y qué insignia lleva en las listas ("Principal", "Cuidador").
 */
enum class UserRole {
    /** El dueño de la cuenta. Tiene todos los permisos y no se le pueden quitar. */
    PRINCIPAL,

    /** Familiar o personal de salud que vigila la insulina sin administrar el estuche. */
    CAREGIVER,

    /** Puede además gestionar a otros usuarios, por delegación del principal. */
    SECONDARY_ADMIN
}

/**
 * Lo que una persona autorizada puede hacer. Un campo por interruptor de la
 * pantalla "Permisos de Usuario".
 */
data class UserPermissions(
    val monitorTemperature: Boolean = false,
    val viewLocation: Boolean = false,
    val viewAccessLogs: Boolean = false,
    val receiveCriticalAlerts: Boolean = false,
    val exportReports: Boolean = false,
    val manageUsers: Boolean = false
) {
    companion object {
        /** Todo permitido: lo que tiene el principal. */
        val ALL = UserPermissions(
            monitorTemperature = true,
            viewLocation = true,
            viewAccessLogs = true,
            receiveCriticalAlerts = true,
            exportReports = true,
            manageUsers = true
        )

        /**
         * Permisos con los que empieza cada rol (configuración de fábrica; el
         * principal puede cambiarlos después uno a uno).
         *
         * Un cuidador empieza viendo lo que necesita para cuidar (temperatura,
         * ubicación y alertas) y nada de administración. El administrador
         * secundario empieza con todo, porque para eso se le nombra.
         */
        fun defaultsFor(role: UserRole): UserPermissions = when (role) {
            UserRole.PRINCIPAL, UserRole.SECONDARY_ADMIN -> ALL
            UserRole.CAREGIVER -> UserPermissions(
                monitorTemperature = true,
                viewLocation = true,
                receiveCriticalAlerts = true
            )
        }
    }
}

/**
 * Cada permiso por separado, para que las pantallas puedan listarlos y
 * cambiarlos de uno en uno sin conocer los campos de [UserPermissions].
 */
enum class UserPermission {
    MONITOR_TEMPERATURE,
    VIEW_LOCATION,
    VIEW_ACCESS_LOGS,
    RECEIVE_CRITICAL_ALERTS,
    EXPORT_REPORTS,
    MANAGE_USERS
}

fun UserPermissions.isGranted(permission: UserPermission): Boolean = when (permission) {
    UserPermission.MONITOR_TEMPERATURE -> monitorTemperature
    UserPermission.VIEW_LOCATION -> viewLocation
    UserPermission.VIEW_ACCESS_LOGS -> viewAccessLogs
    UserPermission.RECEIVE_CRITICAL_ALERTS -> receiveCriticalAlerts
    UserPermission.EXPORT_REPORTS -> exportReports
    UserPermission.MANAGE_USERS -> manageUsers
}

/** Copia con un único permiso cambiado; los demás quedan como estaban. */
fun UserPermissions.with(permission: UserPermission, granted: Boolean): UserPermissions =
    when (permission) {
        UserPermission.MONITOR_TEMPERATURE -> copy(monitorTemperature = granted)
        UserPermission.VIEW_LOCATION -> copy(viewLocation = granted)
        UserPermission.VIEW_ACCESS_LOGS -> copy(viewAccessLogs = granted)
        UserPermission.RECEIVE_CRITICAL_ALERTS -> copy(receiveCriticalAlerts = granted)
        UserPermission.EXPORT_REPORTS -> copy(exportReports = granted)
        UserPermission.MANAGE_USERS -> copy(manageUsers = granted)
    }

/**
 * En qué punto está el acceso de una persona.
 *
 * Hoy la app solo registra e invita: que el invitado entre desde su propio
 * teléfono es una mejora futura, así que nadie pasa todavía a [ACTIVE].
 */
enum class AccessStatus {
    /** Registrada e invitada; aún no ha entrado con su propia cuenta. */
    INVITATION_PENDING,

    /** Ya usa su acceso. */
    ACTIVE
}

/**
 * Persona a la que el dueño del estuche ha dado acceso.
 *
 * @param id identificador del registro; vacío para un usuario aún no guardado.
 * @param status empieza como invitación pendiente: es lo único que la app
 * puede afirmar de alguien recién registrado.
 * @param lastAccessAt último acceso al estuche (epoch ms), o null si no se
 * conoce: la UI muestra entonces "--", nunca una fecha inventada.
 */
data class AuthorizedUser(
    val id: String = "",
    val name: String,
    val email: String,
    val phone: String,
    val role: UserRole,
    val permissions: UserPermissions = UserPermissions.defaultsFor(role),
    val status: AccessStatus = AccessStatus.INVITATION_PENDING,
    val lastAccessAt: Long? = null
)
