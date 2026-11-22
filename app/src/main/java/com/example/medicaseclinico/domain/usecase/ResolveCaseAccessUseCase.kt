package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.CaseAccess
import com.example.medicaseclinico.domain.model.Invitation

/**
 * Decide si la cuenta con sesión iniciada es dueña, invitada o revocada.
 *
 * Está en el dominio porque la revocación tiene un caso delicado que conviene
 * probar en la JVM: si el dueño borra la invitación, esa cuenta no puede
 * pasar a ser "dueña" sin más. Por eso al aceptar una invitación se anota en
 * el perfil de quién es invitado ([guestOf]), y sin invitación esa marca
 * significa acceso revocado.
 */
class ResolveCaseAccessUseCase {

    /**
     * @param uid cuenta con sesión iniciada.
     * @param guestOf dueño cuya invitación aceptó esta cuenta, según su perfil; null si nunca aceptó una.
     * @param invitation invitación vigente para su correo, o null si no hay.
     * @param authorizedUser su registro en la lista del dueño, o null si ya no existe.
     */
    operator fun invoke(
        uid: String,
        guestOf: String?,
        invitation: Invitation?,
        authorizedUser: AuthorizedUser?
    ): CaseAccess = when {
        // Invitarse a uno mismo no convierte al dueño en invitado.
        invitation != null && invitation.ownerId == uid -> CaseAccess.Owner(uid)

        invitation != null && authorizedUser != null -> CaseAccess.Guest(
            ownerId = invitation.ownerId,
            userId = invitation.userId,
            role = authorizedUser.role,
            permissions = authorizedUser.permissions
        )

        // La invitación quedó sin registro (el dueño lo borró): no hay acceso.
        invitation != null -> CaseAccess.Revoked

        guestOf != null && guestOf != uid -> CaseAccess.Revoked

        else -> CaseAccess.Owner(uid)
    }
}
