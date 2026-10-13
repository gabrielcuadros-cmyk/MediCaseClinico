package com.example.medicaseclinico.data.user

import com.example.medicaseclinico.domain.model.AccessStatus
import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.UserPermissions
import com.example.medicaseclinico.domain.model.UserRole

/**
 * Conversión entre un documento de Firestore (un mapa de campos) y el usuario
 * autorizado del dominio.
 *
 * Trabaja con mapas y no con las clases de Firestore para probarse con tests
 * JVM. Lee con tolerancia: un documento puede venir de una versión anterior
 * de la app o haberse editado a mano en la consola.
 */
internal object AuthorizedUserMapper {

    const val FIELD_NAME = "name"
    const val FIELD_EMAIL = "email"
    const val FIELD_PHONE = "phone"
    const val FIELD_ROLE = "role"
    const val FIELD_PERMISSIONS = "permissions"
    const val FIELD_STATUS = "status"
    const val FIELD_LAST_ACCESS_AT = "lastAccessAt"

    private const val PERMISSION_MONITOR_TEMPERATURE = "monitorTemperature"
    private const val PERMISSION_VIEW_LOCATION = "viewLocation"
    private const val PERMISSION_VIEW_ACCESS_LOGS = "viewAccessLogs"
    private const val PERMISSION_RECEIVE_CRITICAL_ALERTS = "receiveCriticalAlerts"
    private const val PERMISSION_EXPORT_REPORTS = "exportReports"
    private const val PERMISSION_MANAGE_USERS = "manageUsers"

    fun toMap(user: AuthorizedUser): Map<String, Any?> = mapOf(
        FIELD_NAME to user.name,
        FIELD_EMAIL to user.email,
        FIELD_PHONE to user.phone,
        FIELD_ROLE to user.role.name,
        FIELD_PERMISSIONS to mapOf(
            PERMISSION_MONITOR_TEMPERATURE to user.permissions.monitorTemperature,
            PERMISSION_VIEW_LOCATION to user.permissions.viewLocation,
            PERMISSION_VIEW_ACCESS_LOGS to user.permissions.viewAccessLogs,
            PERMISSION_RECEIVE_CRITICAL_ALERTS to user.permissions.receiveCriticalAlerts,
            PERMISSION_EXPORT_REPORTS to user.permissions.exportReports,
            PERMISSION_MANAGE_USERS to user.permissions.manageUsers
        ),
        FIELD_STATUS to user.status.name,
        FIELD_LAST_ACCESS_AT to user.lastAccessAt
    )

    /**
     * @return el usuario, o null si al documento le falta el nombre o el rol:
     * sin ellos no se puede mostrar ni saber qué puede hacer, y es más seguro
     * no listarlo que inventarle un rol.
     */
    fun fromMap(id: String, data: Map<String, Any?>): AuthorizedUser? {
        val name = (data[FIELD_NAME] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val role = (data[FIELD_ROLE] as? String)
            ?.let { stored -> UserRole.entries.firstOrNull { it.name == stored } }
            ?: return null
        val permissions = data[FIELD_PERMISSIONS] as? Map<*, *>

        return AuthorizedUser(
            id = id,
            name = name,
            email = data[FIELD_EMAIL] as? String ?: "",
            phone = data[FIELD_PHONE] as? String ?: "",
            role = role,
            // Un permiso que falta o no es booleano se lee como no concedido.
            permissions = UserPermissions(
                monitorTemperature = permissions.granted(PERMISSION_MONITOR_TEMPERATURE),
                viewLocation = permissions.granted(PERMISSION_VIEW_LOCATION),
                viewAccessLogs = permissions.granted(PERMISSION_VIEW_ACCESS_LOGS),
                receiveCriticalAlerts = permissions.granted(PERMISSION_RECEIVE_CRITICAL_ALERTS),
                exportReports = permissions.granted(PERMISSION_EXPORT_REPORTS),
                manageUsers = permissions.granted(PERMISSION_MANAGE_USERS)
            ),
            // Sin estado reconocible se lee como pendiente: no se puede afirmar
            // que alguien ya usa su acceso si el documento no lo dice.
            status = (data[FIELD_STATUS] as? String)
                ?.let { stored -> AccessStatus.entries.firstOrNull { it.name == stored } }
                ?: AccessStatus.INVITATION_PENDING,
            lastAccessAt = (data[FIELD_LAST_ACCESS_AT] as? Number)?.toLong()
        )
    }

    private fun Map<*, *>?.granted(key: String): Boolean = this?.get(key) == true
}
