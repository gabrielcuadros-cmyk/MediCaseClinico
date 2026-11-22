package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.AuthUser
import com.example.medicaseclinico.domain.model.CaseAccess
import kotlinx.coroutines.flow.Flow

/**
 * Relación de la cuenta con sesión iniciada con el estuche (dueña, invitada o
 * revocada), en tiempo real: si el dueño cambia un permiso o quita el acceso,
 * el `Flow` emite el nuevo estado sin reiniciar la app.
 *
 * Como el resto de repositorios, el `Flow` no silencia errores: quien lo
 * recolecta decide qué hacer si no se puede leer.
 */
interface CaseAccessRepository {
    fun observeAccess(user: AuthUser): Flow<CaseAccess>
}
