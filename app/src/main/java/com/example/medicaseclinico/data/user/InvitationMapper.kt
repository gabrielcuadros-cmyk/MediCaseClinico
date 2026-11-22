package com.example.medicaseclinico.data.user

import com.example.medicaseclinico.domain.model.Invitation

/**
 * Invitaciones en Firestore: `invitations/{correo}` con el dueño y el registro
 * del invitado.
 *
 * El id del documento es el correo normalizado: así quien inicia sesión
 * encuentra su invitación leyendo un solo documento, y las reglas de
 * seguridad pueden comprobar que solo él la lee. Trabaja con mapas para
 * probarse con tests JVM.
 */
internal object InvitationMapper {
    const val COLLECTION = "invitations"
    const val FIELD_OWNER_ID = "ownerId"
    const val FIELD_USER_ID = "userId"

    /** En el perfil del invitado: de quién aceptó la invitación (para detectar la revocación). */
    const val FIELD_GUEST_OF = "guestOf"

    /**
     * Correo como id de documento: sin espacios y en minúsculas, igual que lo
     * guarda Firebase Auth. Vacío si no hay correo.
     */
    fun documentIdFor(email: String?): String = email.orEmpty().trim().lowercase()

    fun toMap(invitation: Invitation): Map<String, Any> = mapOf(
        FIELD_OWNER_ID to invitation.ownerId,
        FIELD_USER_ID to invitation.userId
    )

    /** null si al documento le falta el dueño o el registro (no es una invitación válida). */
    fun fromMap(data: Map<String, Any?>): Invitation? {
        val ownerId = (data[FIELD_OWNER_ID] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val userId = (data[FIELD_USER_ID] as? String)?.takeIf { it.isNotBlank() } ?: return null
        return Invitation(ownerId = ownerId, userId = userId)
    }
}
