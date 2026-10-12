package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.UserProfile

/**
 * Contrato del perfil de usuario guardado en la nube.
 *
 * Mismas reglas que el resto de repositorios: las operaciones `suspend`
 * devuelven `Result` y nunca lanzan.
 */
interface UserProfileRepository {

    /** Crea el perfil o actualiza sus campos; no borra otros que ya tuviera el documento. */
    suspend fun saveProfile(profile: UserProfile): Result<Unit>
}
