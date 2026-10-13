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
 * Persona a la que el dueño del estuche ha dado acceso.
 *
 * @param id identificador del registro; vacío para un usuario aún no guardado.
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
    val lastAccessAt: Long? = null
)
